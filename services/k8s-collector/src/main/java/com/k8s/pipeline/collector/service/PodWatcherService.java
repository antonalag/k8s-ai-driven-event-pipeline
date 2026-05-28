package com.k8s.pipeline.collector.service;

import com.k8s.pipeline.collector.model.KubernetesEvent;
import com.k8s.pipeline.collector.model.PodPhase;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Spring service that watches Kubernetes Pod state changes using the official
 * Kubernetes Java client Informer API and publishes each event to Kafka.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Starts when Spring signals {@link ApplicationReadyEvent} — guarantees the
 *       full application context is wired before connecting to the cluster.</li>
 *   <li>Shuts down cleanly via {@link #shutdown()} annotated with {@link PreDestroy}.</li>
 * </ol>
 *
 * <p>Each Pod add/update event is mapped to a {@link KubernetesEvent} record,
 * logged via SLF4J, and published to the {@value #TOPIC} Kafka topic.
 * The message key is {@code "<namespace>/<podName>"} to guarantee ordering
 * per Pod (same key → same partition).
 */
@Service
public class PodWatcherService {

    private static final Logger log = LoggerFactory.getLogger(PodWatcherService.class);

    /** Kafka topic defined in the contract (Milestone 4). */
    static final String TOPIC = "k8s-pod-events";

    /** Resync period for the SharedIndexInformer (0 = no periodic resync). */
    private static final long RESYNC_PERIOD_MS = 0L;

    private final KafkaTemplate<String, KubernetesEvent> kafkaTemplate;

    private SharedInformerFactory informerFactory;

    /**
     * Constructor injection — mandatory dependency on {@link KafkaTemplate}.
     * Spring Boot auto-configures the template from {@code application.properties}.
     */
    public PodWatcherService(KafkaTemplate<String, KubernetesEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Initialises the Kubernetes client, registers the Pod informer, and starts
     * watching. Triggered after the Spring context is fully ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("PodWatcherService starting — connecting to Kubernetes cluster...");

        ApiClient apiClient = buildApiClient();
        if (apiClient == null) {
            log.error("Failed to build Kubernetes ApiClient. PodWatcherService will not start.");
            return;
        }

        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        informerFactory = new SharedInformerFactory(apiClient);

        SharedIndexInformer<V1Pod> podInformer = informerFactory.sharedIndexInformerFor(
                callParams -> coreV1Api.listPodForAllNamespaces()
                        .resourceVersion(callParams.resourceVersion)
                        .watch(callParams.watch)
                        .timeoutSeconds(callParams.timeoutSeconds)
                        .buildCall(null),
                V1Pod.class,
                V1PodList.class,
                RESYNC_PERIOD_MS
        );

        podInformer.addEventHandler(new ResourceEventHandler<>() {

            @Override
            public void onAdd(V1Pod pod) {
                handlePodEvent("ADDED", pod);
            }

            @Override
            public void onUpdate(V1Pod oldPod, V1Pod newPod) {
                handlePodEvent("UPDATED", newPod);
            }

            @Override
            public void onDelete(V1Pod pod, boolean deletedFinalStateUnknown) {
                log.debug("Pod DELETED — name={}, namespace={}, finalStateUnknown={}",
                        podName(pod), namespace(pod), deletedFinalStateUnknown);
            }
        });

        informerFactory.startAllRegisteredInformers();
        log.info("PodWatcherService started — watching Pod events across all namespaces.");
    }

    /**
     * Stops all informers and releases resources when the Spring context closes.
     */
    @PreDestroy
    public void shutdown() {
        if (informerFactory != null) {
            log.info("PodWatcherService shutting down — stopping all informers...");
            informerFactory.stopAllRegisteredInformers();
            log.info("PodWatcherService stopped.");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Event handling
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Maps a {@link V1Pod} to a {@link KubernetesEvent}, logs it, and publishes
     * it to the {@value #TOPIC} Kafka topic.
     *
     * <p>The message key is {@code "<namespace>/<podName>"} — this ensures all
     * events for the same Pod land on the same partition, preserving order.
     *
     * @param eventType human-readable label ("ADDED" or "UPDATED")
     * @param pod       the Pod object received from the informer
     */
    private void handlePodEvent(String eventType, V1Pod pod) {
        try {
            KubernetesEvent event = toKubernetesEvent(pod);
            String messageKey = event.namespace() + "/" + event.podName();

            log.info("[{}] pod={}, namespace={}, status={}, timestamp={}",
                    eventType,
                    event.podName(),
                    event.namespace(),
                    event.status(),
                    event.timestamp());

            CompletableFuture<SendResult<String, KubernetesEvent>> future =
                    kafkaTemplate.send(TOPIC, messageKey, event);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event for pod={} to topic={}: {}",
                            event.podName(), TOPIC, ex.getMessage(), ex);
                } else {
                    log.debug("Published event for pod={} to topic={} partition={} offset={}",
                            event.podName(),
                            TOPIC,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });

        } catch (Exception e) {
            log.warn("Could not process Pod event [{}]: {}", eventType, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mapping
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Converts a {@link V1Pod} into a {@link KubernetesEvent} record.
     *
     * <p>The Pod phase string is parsed safely via {@link #parsePodPhase(String)};
     * any unrecognised value falls back to {@link PodPhase#Unknown}.
     *
     * @param pod the Pod received from the Kubernetes API
     * @return a fully populated, immutable {@link KubernetesEvent}
     */
    private KubernetesEvent toKubernetesEvent(V1Pod pod) {
        String name     = podName(pod);
        String ns       = namespace(pod);
        String phaseStr = (pod.getStatus() != null) ? pod.getStatus().getPhase() : null;
        PodPhase phase  = parsePodPhase(phaseStr);
        Instant ts      = Instant.now();

        return new KubernetesEvent(name, ns, phase, ts);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds the {@link ApiClient} from the default cluster configuration.
     * In-cluster config is used when running inside Kubernetes; kubeconfig is
     * used when running locally.
     *
     * @return a configured {@link ApiClient}, or {@code null} if initialisation fails
     */
    private ApiClient buildApiClient() {
        try {
            ApiClient client = Config.defaultClient();
            log.debug("Kubernetes ApiClient initialised from default config.");
            return client;
        } catch (IOException e) {
            log.error("Could not load Kubernetes configuration: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Safely parses a Kubernetes Pod phase string into a {@link PodPhase} enum value.
     * Returns {@link PodPhase#Unknown} for {@code null} or unrecognised values.
     *
     * @param phase the raw phase string from the Kubernetes API (may be {@code null})
     * @return the corresponding {@link PodPhase}, never {@code null}
     */
    private PodPhase parsePodPhase(String phase) {
        if (phase == null || phase.isBlank()) {
            return PodPhase.Unknown;
        }
        try {
            return PodPhase.valueOf(phase);
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognised Pod phase '{}' — defaulting to Unknown.", phase);
            return PodPhase.Unknown;
        }
    }

    /**
     * Extracts the Pod name defensively, returning {@code "unknown"} if metadata is absent.
     */
    private String podName(V1Pod pod) {
        if (pod.getMetadata() == null || pod.getMetadata().getName() == null) {
            return "unknown";
        }
        return pod.getMetadata().getName();
    }

    /**
     * Extracts the Pod namespace defensively, returning {@code "unknown"} if metadata is absent.
     */
    private String namespace(V1Pod pod) {
        if (pod.getMetadata() == null || pod.getMetadata().getNamespace() == null) {
            return "unknown";
        }
        return pod.getMetadata().getNamespace();
    }
}
