package com.platform.analyzer.infrastructure.persistence.opensearch;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.ports.AiAnalysisRepositoryPort;
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

    private final SpringDataAiAnalysisRepository springDataRepository;

    public OpenSearchAnalysisRepository(SpringDataAiAnalysisRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void save(AiAnalysis analysis) {
        AiAnalysisDocument doc = AiAnalysisDocument.from(analysis);
        springDataRepository.save(doc);
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
