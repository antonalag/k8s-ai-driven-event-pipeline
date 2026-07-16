package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.DismissalResult;

/**
 * Inbound port defining the contract for dismissing an analysis by its document ID.
 *
 * <p>Zero Spring imports — pure domain.</p>
 */
public interface DismissAnalysisUseCase {

    /**
     * Dismisses the analysis identified by the given ID.
     *
     * @param analysisId the analysis document identifier
     * @param reason     optional operator-provided reason (may be null)
     * @return the result containing the analysis ID and its new status
     * @throws AnalysisNotFoundException        if no analysis exists with the given ID
     * @throws AnalysisAlreadyResolvedException if the analysis is already in a terminal state
     */
    DismissalResult dismiss(String analysisId, String reason);
}
