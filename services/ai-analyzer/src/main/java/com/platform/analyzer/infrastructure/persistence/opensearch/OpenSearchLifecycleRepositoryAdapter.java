package com.platform.analyzer.infrastructure.persistence.opensearch;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.AnalysisLifecycle;
import com.platform.analyzer.domain.model.AnalysisStatus;
import com.platform.analyzer.domain.ports.AnalysisLifecycleRepositoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Adapter implementing AnalysisLifecycleRepositoryPort using OpenSearch.
 * Activated when platform.storage.type=opensearch.
 * Legacy documents with null status are treated as PENDING.
 */
@Component
@ConditionalOnProperty(name = "platform.storage.type", havingValue = "opensearch")
public class OpenSearchLifecycleRepositoryAdapter implements AnalysisLifecycleRepositoryPort {

    private final SpringDataAiAnalysisRepository repository;

    public OpenSearchLifecycleRepositoryAdapter(SpringDataAiAnalysisRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AnalysisLifecycle> findById(String id) {
        return repository.findById(id)
            .map(this::toLifecycle);
    }

    @Override
    public void save(AnalysisLifecycle lifecycle) {
        repository.findById(lifecycle.getId()).ifPresent(doc -> {
            doc.setStatus(lifecycle.getStatus().name());
            if (lifecycle.getResolvedAt() != null) {
                doc.setResolvedAt(lifecycle.getResolvedAt()
                    .toInstant(ZoneOffset.UTC));
            }
            doc.setResolutionReason(lifecycle.getResolutionReason());
            repository.save(doc);
        });
    }

    @Override
    public void updateStatus(String id, String status,
                             String resolvedAt, String resolutionReason) {
        repository.findById(id).ifPresent(doc -> {
            doc.setStatus(status);
            doc.setResolvedAt(Instant.parse(resolvedAt));
            doc.setResolutionReason(resolutionReason);
            repository.save(doc);
        });
    }

    private AnalysisLifecycle toLifecycle(AiAnalysisDocument doc) {
        AiAnalysis analysis = doc.toDomain();
        AnalysisStatus status = doc.getStatus() != null
            ? AnalysisStatus.valueOf(doc.getStatus())
            : AnalysisStatus.PENDING;
        LocalDateTime resolvedAt = doc.getResolvedAt() != null
            ? LocalDateTime.ofInstant(doc.getResolvedAt(), ZoneOffset.UTC)
            : null;
        return new AnalysisLifecycle(
            doc.getId(), analysis, status, resolvedAt, doc.getResolutionReason());
    }
}
