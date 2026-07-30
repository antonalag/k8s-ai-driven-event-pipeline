package com.platform.analyzer.infrastructure.adapter.inbound.messaging;

import com.platform.analyzer.domain.model.valueobject.AiAnalysis;
import com.platform.analyzer.domain.model.event.AiAnalysisEvent;
import com.platform.analyzer.domain.port.outbound.AiAnalysisRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka consumer that persists AiAnalysisEvent into the storage layer.
 * Uses the full 8-arg AiAnalysis constructor to preserve MCP intelligence fields.
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
                    "spring.json.value.default.type=com.platform.analyzer.domain.model.event.AiAnalysisEvent",
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
                    event.recommendedActions() != null ? event.recommendedActions() : List.of(),
                    event.mcpToolsUsed() != null ? event.mcpToolsUsed() : List.of(),
                    event.mcpContextAvailable(),
                    event.modelUsed() != null ? event.modelUsed() : "unknown"
            );
            repositoryPort.save(analysis);
            log.info("[STORAGE] Persisted analysis for pod='{}'", event.podName());
        } catch (Exception e) {
            log.error("[STORAGE] Failed to persist analysis for pod='{}': {}",
                    event.podName(), e.getMessage(), e);
        }
    }
}
