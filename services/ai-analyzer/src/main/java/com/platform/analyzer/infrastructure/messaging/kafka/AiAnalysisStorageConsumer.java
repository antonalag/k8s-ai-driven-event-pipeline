package com.platform.analyzer.infrastructure.messaging.kafka;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.AiAnalysisEvent;
import com.platform.analyzer.domain.ports.AiAnalysisRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that persists AiAnalysisEvent into the storage layer.
 */
@Component
@ConditionalOnProperty(name = "platform.messaging.type", havingValue = "kafka")
public class AiAnalysisStorageConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisStorageConsumer.class);

    private final AiAnalysisRepositoryPort repositoryPort;

    public AiAnalysisStorageConsumer(AiAnalysisRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @KafkaListener(
            topics = "ai-analysis-events",
            groupId = "ai-storage-group",
            properties = {
                    "spring.json.value.default.type=com.platform.analyzer.domain.model.AiAnalysisEvent",
                    "spring.json.use.type.headers=false"
            }
    )
    public void onAnalysisEvent(AiAnalysisEvent event) {
        log.info("[STORAGE] Received analysis event for pod='{}' verdict='{}'",
                event.podName(), event.verdict());
        try {
            AiAnalysis analysis = new AiAnalysis(
                    event.podName(),
                    event.namespace(),
                    event.verdict(),
                    event.rootCauseAnalysis(),
                    event.recommendedActions()
            );
            repositoryPort.save(analysis);
            log.info("[STORAGE] Persisted analysis for pod='{}'", event.podName());
        } catch (Exception e) {
            log.error("[STORAGE] Failed to persist analysis for pod='{}': {}",
                    event.podName(), e.getMessage(), e);
        }
    }
}
