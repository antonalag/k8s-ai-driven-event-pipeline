package com.platform.analyzer.infrastructure.messaging.kafka;

import com.platform.analyzer.domain.model.AnalysisLifecycleEvent;
import com.platform.analyzer.domain.ports.AnalysisLifecycleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that updates the lifecycle status of analysis documents
 * upon receiving {@code LIFECYCLE_CHANGE} events on the {@code ai-analysis-events} topic.
 */
@Component
@ConditionalOnProperty(name = "platform.messaging.type", havingValue = "kafka")
public class AnalysisLifecycleConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisLifecycleConsumer.class);

    private final AnalysisLifecycleRepositoryPort repository;

    public AnalysisLifecycleConsumer(AnalysisLifecycleRepositoryPort repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = "ai-analysis-events",
            groupId = "ai-lifecycle-group",
            properties = {
                    "spring.json.value.default.type=com.platform.analyzer.domain.model.AnalysisLifecycleEvent",
                    "spring.json.use.type.headers=false"
            }
    )
    public void onLifecycleEvent(AnalysisLifecycleEvent event) {
        if (!"LIFECYCLE_CHANGE".equals(event.eventType())) {
            return; // Skip non-lifecycle events
        }

        log.info("[LIFECYCLE-STORAGE] Received lifecycle event for analysis='{}' status='{}'",
                event.analysisId(), event.newStatus());

        try {
            repository.updateStatus(
                    event.analysisId(),
                    event.newStatus(),
                    event.resolvedAt().toString(),
                    event.resolutionReason()
            );
        } catch (Exception e) {
            log.warn("[LIFECYCLE-STORAGE] Failed to update document '{}': {}. Skipping.",
                    event.analysisId(), e.getMessage());
        }
    }
}
