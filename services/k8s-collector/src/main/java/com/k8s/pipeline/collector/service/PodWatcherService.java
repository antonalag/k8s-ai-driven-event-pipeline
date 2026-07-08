package com.k8s.pipeline.collector.service;

import com.k8s.pipeline.collector.domain.model.KubernetesEvent;
import com.k8s.pipeline.collector.domain.model.PodPhase;
import com.k8s.pipeline.collector.domain.ports.EventPublisherPort;
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

/**
 * Service that watches Kubernetes Pod state changes and publishes events
 * via the EventPublisherPort.
 */
@Service
public class PodWatcherService {

    private static final Logger log = LoggerFactory.getLogger(PodWatcherService.class);

    static final String TOPIC = "k8s-pod-events";
    private static final long RESYNC_PERIOD_MS = 0L;

    private final EventPublisherPort eventPublisher;

    private SharedInformerFactory informerFactory;

    public PodWatcherService(EventPublisherPort eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

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
                handlePodTermination(pod);
            }
        });

        informerFactory.startAllRegisteredInformers();
        log.info("PodWatcherService started — watching Pod events across all namespaces.");
    }

    @PreDestroy
    public void shutdown() {
        if (informerFactory != null) {
            log.info("PodWatcherService shutting down — stopping all informers...");
            informerFactory.stopAllRegisteredInformers();
            log.info("PodWatcherService stopped.");
        }
    }

    private void handlePodEvent(String eventType, V1Pod pod) {
        try {
            KubernetesEvent event = toKubernetesEvent(pod);

            log.info("[{}] pod={}, namespace={}, status={}, timestamp={}",
                    eventType,
                    event.podName(),
                    event.namespace(),
                    event.status(),
                    event.timestamp());

            eventPublisher.publish(event);

        } catch (Exception e) {
            log.warn("Could not process Pod event [{}]: {}", eventType, e.getMessage());
        }
    }

    /**
     * Publishes a Succeeded event for deleted pods.
     * This closes the diagnostic loop — the ai-analyzer will generate a HEALTHY verdict,
     * causing the card to disappear from the UI.
     */
    private void handlePodTermination(V1Pod pod) {
        try {
            String name = podName(pod);
            String ns = namespace(pod);

            KubernetesEvent event = new KubernetesEvent(name, ns, PodPhase.Succeeded, Instant.now());

            log.info("[DELETED] pod={}, namespace={}, publishedAs=Succeeded", name, ns);
            eventPublisher.publish(event);

        } catch (Exception e) {
            log.warn("Could not process Pod DELETE event: {}", e.getMessage());
        }
    }

    private KubernetesEvent toKubernetesEvent(V1Pod pod) {
        String name     = podName(pod);
        String ns       = namespace(pod);
        String phaseStr = (pod.getStatus() != null) ? pod.getStatus().getPhase() : null;
        PodPhase phase  = parsePodPhase(phaseStr);
        Instant ts      = Instant.now();

        return new KubernetesEvent(name, ns, phase, ts);
    }

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

    private String podName(V1Pod pod) {
        if (pod.getMetadata() == null || pod.getMetadata().getName() == null) {
            return "unknown";
        }
        return pod.getMetadata().getName();
    }

    private String namespace(V1Pod pod) {
        if (pod.getMetadata() == null || pod.getMetadata().getNamespace() == null) {
            return "unknown";
        }
        return pod.getMetadata().getNamespace();
    }
}
