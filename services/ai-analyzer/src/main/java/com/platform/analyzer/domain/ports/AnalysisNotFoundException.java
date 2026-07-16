package com.platform.analyzer.domain.ports;

/**
 * Domain exception thrown when an analysis document cannot be found by its ID.
 *
 * <p>Zero Spring imports — pure domain.</p>
 */
public class AnalysisNotFoundException extends RuntimeException {

    private final String analysisId;

    public AnalysisNotFoundException(String analysisId) {
        super("Analysis not found: " + analysisId);
        this.analysisId = analysisId;
    }

    public String getAnalysisId() {
        return analysisId;
    }
}
