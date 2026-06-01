package com.platform.analyzer.infrastructure.messaging.kafka;

import com.platform.analyzer.domain.model.AiAnalysisEvent;
import com.platform.analyzer.domain.ports.LmMessagingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Adapter implementing LmMessagingPort using Apache Kafka.
 * Activated when platform.messaging.type=kafka.
 */
@Component
@ConditionalOnProperty(name = "platform.messaging.type", havingValue = "kafka")
public class KafkaMessagingAdapter implements LmMessagingPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessagingAdapter.class);

    static final String TOPIC = "ai-analysis-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaMessagingAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
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
