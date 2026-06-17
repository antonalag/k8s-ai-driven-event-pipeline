package com.platform.analyzer.infrastructure.persistence.opensearch;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.ports.AiAnalysisRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter implementing AiAnalysisRepositoryPort using OpenSearch.
 * Activated when platform.storage.type=opensearch.
 */
@Component
@ConditionalOnProperty(name = "platform.storage.type", havingValue = "opensearch")
public class OpenSearchAnalysisRepository implements AiAnalysisRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchAnalysisRepository.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 5000;

    private final SpringDataAiAnalysisRepository springDataRepository;

    public OpenSearchAnalysisRepository(SpringDataAiAnalysisRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void save(AiAnalysis analysis) {
        AiAnalysisDocument doc = AiAnalysisDocument.from(analysis);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                springDataRepository.save(doc);
                return;
            } catch (Exception e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("OpenSearch persistence failed after {} attempts for pod '{}'. Cause: {}",
                            MAX_ATTEMPTS, analysis.podName(), e.getMessage());
                    return;
                }
                long delay = Math.min(BASE_DELAY_MS * (1L << (attempt - 1)), MAX_DELAY_MS);
                log.warn("OpenSearch persistence attempt {}/{} failed for pod '{}'. Retrying in {}ms. Cause: {}",
                        attempt, MAX_ATTEMPTS, analysis.podName(), delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("OpenSearch retry interrupted for pod '{}'", analysis.podName());
                    return;
                }
            }
        }
    }

    @Override
    public List<AiAnalysis> findByPodName(String podName) {
        return springDataRepository.findByPodNameOrderByAnalyzedAtDesc(podName)
                .stream()
                .map(AiAnalysisDocument::toDomain)
                .toList();
    }

    @Override
    public List<AiAnalysis> findByVerdict(String verdict) {
        return springDataRepository.findByVerdict(verdict)
                .stream()
                .map(AiAnalysisDocument::toDomain)
                .toList();
    }
}
