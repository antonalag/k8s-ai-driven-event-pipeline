package com.platform.analyzer.infrastructure;

import com.platform.analyzer.model.AiAnalysisDocument;
import com.platform.analyzer.model.AiAnalysisEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that persists every {@link AiAnalysisEvent} from the
 * {@code ai-analysis-events} topic into the {@code ai-analysis-reports}
 * OpenSearch index.
 *
 * <h2>Role in the pipeline</h2>
 * <p>This consumer closes the Storage Layer (Phase 4) loop:
 * <pre>
 *   k8s-pod-events  →  PodEventConsumer  →  OllamaAnalyzerService
 *       →  AiAnalysisProducer  →  ai-analysis-events
 *       →  AiAnalysisStorageConsumer  →  OpenSearch (ai-analysis-reports)
 * </pre>
 *
 * <h2>Consumer group isolation</h2>
 * <p>A dedicated consumer group ({@code ai-storage-group}) is used so that this
 * consumer maintains its own offset independently of any future consumers of the
 * same topic (e.g. alerting, dashboards). Each group receives all messages.
 *
 * <h2>Idempotency</h2>
 * <p>The document ID is {@code {podName}-{analyzedAt.epochMilli}}, so re-processing
 * the same event (e.g. after a consumer restart) overwrites the existing document
 * rather than creating a duplicate.
 */
@Component
public class AiAnalysisStorageConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisStorageConsumer.class);

    private final AiAnalysisRepository repository;

    /**
     * Constructor injection — preferred over field injection per project standards.
     *
     * @param repository Spring Data OpenSearch repository for {@link AiAnalysisDocument}
     */
    public AiAnalysisStorageConsumer(AiAnalysisRepository repository) {
        this.repository = repository;
    }

    /**
     * Receives an {@link AiAnalysisEvent} from the {@code ai-analysis-events} topic,
     * converts it to an {@link AiAnalysisDocument}, and persists it to OpenSearch.
     *
     * @param event the structured AI analysis event to persist
     */
    @KafkaListener(
            topics = "ai-analysis-events",
            groupId = "ai-storage-group"
    )
    public void onAnalysisEvent(AiAnalysisEvent event) {
        log.info("[STORAGE] Received analysis event for pod='{}' verdict='{}'",
                event.podName(), event.verdict());

        try {
            AiAnalysisDocument document = AiAnalysisDocument.from(event);
            AiAnalysisDocument saved = repository.save(document);
            log.info("[STORAGE] Persisted document id='{}' to index 'ai-analysis-reports'", saved.getId());
        } catch (Exception e) {
            log.error("[STORAGE] Failed to persist analysis for pod='{}': {}",
                    event.podName(), e.getMessage(), e);
        }
    }
}
