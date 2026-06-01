package com.k8s.pipeline.collector.infrastructure.messaging.kafka;

import com.k8s.pipeline.collector.domain.model.KubernetesEvent;
import com.k8s.pipeline.collector.domain.ports.EventPublishException;
import com.k8s.pipeline.collector.domain.ports.EventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Adapter implementing EventPublisherPort using Apache Kafka.
 * Activated when platform.messaging.type=kafka.
 */
@Component
@ConditionalOnProperty(name = "platform.messaging.type", havingValue = "kafka")
public class KafkaEventPublisher implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    static final String TOPIC = "k8s-pod-events";

    private final KafkaTemplate<String, KubernetesEvent> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, KubernetesEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(KubernetesEvent event) {
        String messageKey = event.namespace() + "/" + event.podName();
        try {
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
            throw new EventPublishException(
                    "Failed to publish event for pod: " + event.podName(), e);
        }
    }
}
