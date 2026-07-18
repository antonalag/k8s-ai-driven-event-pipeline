package com.platform.analyzer.infrastructure.persistence.opensearch;

import com.platform.analyzer.domain.model.AnalysisStatus;
import com.platform.analyzer.domain.model.AuditLogEntry;
import com.platform.analyzer.domain.model.AuditLogPage;
import com.platform.analyzer.domain.ports.AuditLogQueryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter implementing AuditLogQueryPort using OpenSearch with native pagination.
 * Returns only resolved (REMEDIATED/DISMISSED) analyses sorted by resolvedAt DESC.
 */
@Component
@ConditionalOnProperty(name = "platform.storage.type", havingValue = "opensearch")
public class OpenSearchAuditLogQueryAdapter implements AuditLogQueryPort {

    private final SpringDataAiAnalysisRepository repository;

    public OpenSearchAuditLogQueryAdapter(SpringDataAiAnalysisRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditLogPage findResolvedAnalyses(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "resolvedAt"));
        Page<AiAnalysisDocument> result = repository.findByStatusIn(
                List.of("REMEDIATED", "DISMISSED"), pageable);

        List<AuditLogEntry> entries = result.getContent().stream()
                .map(this::toEntry)
                .toList();

        return new AuditLogPage(entries, result.getTotalElements(), page, result.getTotalPages());
    }

    private AuditLogEntry toEntry(AiAnalysisDocument doc) {
        return new AuditLogEntry(
                doc.getResolvedAt(),
                doc.getPodName(),
                doc.getNamespace(),
                doc.getVerdict(),
                doc.getStatus() != null ? AnalysisStatus.valueOf(doc.getStatus()) : AnalysisStatus.PENDING,
                doc.getRootCauseAnalysis(),
                doc.getRecommendedActions(),
                doc.getModelUsed() != null && !doc.getModelUsed().isBlank()
                        ? doc.getModelUsed() : "unknown"
        );
    }
}
