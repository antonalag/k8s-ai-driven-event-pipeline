package com.platform.analyzer.domain.model;

/**
 * Value object representing the outcome of a successful dismissal operation.
 */
public record DismissalResult(
        String analysisId,
        AnalysisStatus newStatus
) {
}
