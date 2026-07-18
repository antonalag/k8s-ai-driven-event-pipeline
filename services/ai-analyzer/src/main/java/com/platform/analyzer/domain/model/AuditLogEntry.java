package com.platform.analyzer.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Read model for a single resolved analysis entry in the audit log.
 * Pure domain — no framework annotations.
 */
public record AuditLogEntry(
        Instant resolvedAt,
        String podName,
        String namespace,
        String verdict,
        AnalysisStatus status,
        String rootCauseAnalysis,
        List<String> recommendedActions,
        String modelUsed
) {
    public AuditLogEntry {
        if (recommendedActions == null) recommendedActions = List.of();
        if (modelUsed == null || modelUsed.isBlank()) modelUsed = "unknown";
    }
}
