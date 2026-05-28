package com.platform.analyzer.infrastructure;

import com.platform.analyzer.model.KubernetesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens to the {@code k8s-pod-events} topic and receives
 * {@link KubernetesEvent} records published by the k8s-collector service.
 *
 * <p>This component is the entry point of the Intelligence Layer (Phase 3).
 * In this initial milestone it logs each received event to confirm end-to-end
 * connectivity between the collector and the analyzer. AI analysis logic will
 * be wired in subsequent milestones.
 */
@Component
public class PodEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PodEventConsumer.class);

    /**
     * Receives a {@link KubernetesEvent} from the {@code k8s-pod-events} topic.
     *
     * <p>Spring Kafka deserializes the JSON message payload into a {@link KubernetesEvent}
     * record automatically using the {@code JsonDeserializer} configured in
     * {@code application.properties}.
     *
     * @param event the deserialized Kubernetes Pod event
     */
    @KafkaListener(
            topics = "k8s-pod-events",
            groupId = "ai-analyzer-group"
    )
    public void onPodEvent(KubernetesEvent event) {
        log.info("Evento recibido en el Analyzer: {}", event);
    }
}
