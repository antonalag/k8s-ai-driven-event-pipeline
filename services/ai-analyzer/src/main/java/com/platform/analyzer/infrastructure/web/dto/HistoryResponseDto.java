package com.platform.analyzer.infrastructure.web.dto;

import com.platform.analyzer.domain.model.AuditLogEntry;

import java.time.Instant;
import java.util.List;

/**
 * API response DTO for a single resolved analysis entry in the audit log history.
 * Maps from {@link AuditLogEntry} — decouples the web layer from the domain read model.
 */
public record HistoryResponseDto(
        Instant resolvedAt,
        String podName,
        String namespace,
        String verdict,
        String status,
        String rootCauseAnalysis,
        List<String> recommendedActions,
        String modelUsed
) {

    /**
     * Factory method mapping an {@link AuditLogEntry} to this DTO.
     * Converts the {@code AnalysisStatus} enum to its String name representation.
     */
    public static HistoryResponseDto from(AuditLogEntry entry) {
        return new HistoryResponseDto(
                entry.resolvedAt(),
                entry.podName(),
                entry.namespace(),
                entry.verdict(),
                entry.status().name(),
                entry.rootCauseAnalysis(),
                entry.recommendedActions(),
                entry.modelUsed()
        );
    }
}
