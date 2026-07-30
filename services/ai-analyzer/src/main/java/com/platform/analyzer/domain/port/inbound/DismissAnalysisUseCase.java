package com.platform.analyzer.domain.port.inbound;

import com.platform.analyzer.domain.exception.AnalysisAlreadyResolvedException;
import com.platform.analyzer.domain.exception.AnalysisNotFoundException;
import com.platform.analyzer.domain.model.valueobject.DismissalResult;

/**
 * Inbound port defining the contract for dismissing an analysis by its document ID.
 */
public interface DismissAnalysisUseCase {

    /**
     * @param analysisId the analysis document identifier
     * @param reason     optional operator-provided reason (may be null)
     * @return the result containing the analysis ID and its new status
     * @throws AnalysisNotFoundException        if no analysis exists with the given ID
     * @throws AnalysisAlreadyResolvedException if the analysis is already in a terminal state
     */
    DismissalResult dismiss(String analysisId, String reason);
}
