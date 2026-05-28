package com.platform.analyzer.infrastructure;

import com.platform.analyzer.model.AiAnalysisEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer that publishes structured {@link AiAnalysisEvent} records to the
 * {@code ai-analysis-events} topic, closing the intelligence pipeline loop.
 *
 * <p>The {@code ai-analyzer} service is a hybrid Consumer+Producer:
 * <ul>
 *   <li><strong>Consumes</strong> from {@code k8s-pod-events} (via {@link PodEventConsumer})</li>
 *   <li><strong>Produces</strong> to {@code ai-analysis-events} (via this class)</li>
 * </ul>
 *
 * <p>The pod name is used as the Kafka message key to ensure that all analysis events
 * for the same pod land on the same partition, preserving ordering per pod.
 *
 * <p>Sends are non-blocking: the {@link KafkaTemplate} returns a {@link CompletableFuture}
 * whose callbacks log success or failure without blocking the consumer thread.
 */
@Component
public class AiAnalysisProducer {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisProducer.class);

    static final String TOPIC = "ai-analysis-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Constructor injection — preferred over field injection per project standards.
     *
     * @param kafkaTemplate Spring Kafka template configured with {@code JsonSerializer}
     */
    public AiAnalysisProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes an {@link AiAnalysisEvent} to the {@code ai-analysis-events} topic.
     *
     * <p>The pod name is used as the message key so that all events for the same pod
     * are routed to the same partition, preserving per-pod ordering for downstream consumers.
     *
     * <p>The send is asynchronous — success and failure are handled via callbacks
     * to avoid blocking the Kafka consumer thread.
     *
     * @param event the structured AI analysis event to publish
     */
    public void publish(AiAnalysisEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, event.podName(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[PRODUCER] Failed to publish AiAnalysisEvent for pod '{}' to topic '{}': {}",
                        event.podName(), TOPIC, ex.getMessage(), ex);
            } else {
                log.info("[PRODUCER] Published AiAnalysisEvent for pod '{}' → topic='{}' partition={} offset={}",
                        event.podName(),
                        TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
