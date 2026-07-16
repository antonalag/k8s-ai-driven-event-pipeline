package com.platform.analyzer.infrastructure.messaging.kafka;

import com.platform.analyzer.domain.model.AnalysisLifecycleEvent;
import com.platform.analyzer.domain.ports.LifecycleMessagingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Adapter implementing LifecycleMessagingPort using Apache Kafka.
 * Publishes analysis lifecycle state-change events to the {@code ai-analysis-events} topic.
 * Activated when platform.messaging.type=kafka.
 */
@Component
@ConditionalOnProperty(name = "platform.messaging.type", havingValue = "kafka")
public class KafkaLifecycleMessagingAdapter implements LifecycleMessagingPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaLifecycleMessagingAdapter.class);

    static final String TOPIC = "ai-analysis-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaLifecycleMessagingAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishLifecycleEvent(AnalysisLifecycleEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, event.podName(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[LIFECYCLE] Failed to publish event for analysis '{}': {}",
                        event.analysisId(), ex.getMessage(), ex);
            } else {
                log.info("[LIFECYCLE] Published LIFECYCLE_CHANGE for analysis '{}' → {}",
                        event.analysisId(), event.newStatus());
            }
        });
    }
}
