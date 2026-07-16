package com.platform.analyzer.domain.model;

/**
 * Value object representing the outcome of a successful dismissal operation.
 *
 * <p>Zero Spring imports — pure domain.</p>
 */
public record DismissalResult(
        String analysisId,
        AnalysisStatus newStatus
) {
}
