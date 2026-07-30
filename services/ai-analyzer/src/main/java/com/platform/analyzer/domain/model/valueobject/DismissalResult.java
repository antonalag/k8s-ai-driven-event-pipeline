package com.platform.analyzer.domain.model.valueobject;

import com.platform.analyzer.domain.model.enums.AnalysisStatus;

/**
 * Value object representing the outcome of a successful dismissal operation.
 */
public record DismissalResult(
        String analysisId,
        AnalysisStatus newStatus
) {
}
