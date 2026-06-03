package com.platform.analyzer.infrastructure.persistence.opensearch;

import com.platform.analyzer.domain.model.AiAnalysisView;
import com.platform.analyzer.domain.ports.AiAnalysisQueryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Adapter implementing AiAnalysisQueryPort using OpenSearch.
 * Activated when platform.storage.type=opensearch.
 */
@Component
@ConditionalOnProperty(name = "platform.storage.type", havingValue = "opensearch")
public class OpenSearchAnalysisQueryAdapter implements AiAnalysisQueryPort {

    private final SpringDataAiAnalysisRepository repository;

    public OpenSearchAnalysisQueryAdapter(SpringDataAiAnalysisRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AiAnalysisView> findAll() {
        return StreamSupport.stream(repository.findAll().spliterator(), false)
                .sorted(Comparator.comparing(AiAnalysisDocument::getAnalyzedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AiAnalysisDocument::toView)
                .toList();
    }

    @Override
    public List<AiAnalysisView> findByNamespace(String namespace) {
        return repository.findByNamespaceOrderByAnalyzedAtDesc(namespace)
                .stream()
                .map(AiAnalysisDocument::toView)
                .toList();
    }

    @Override
    public List<AiAnalysisView> findByPodName(String podName) {
        return repository.findByPodNameOrderByAnalyzedAtDesc(podName)
                .stream()
                .map(AiAnalysisDocument::toView)
                .toList();
    }
}
