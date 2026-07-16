package com.platform.analyzer.domain.ports;

/**
 * Domain exception thrown when a dismiss operation is attempted
 * on an analysis that is already in a terminal state (DISMISSED or REMEDIATED).
 */
public class AnalysisAlreadyResolvedException extends RuntimeException {

    private final String analysisId;
    private final String currentStatus;

    public AnalysisAlreadyResolvedException(String analysisId, String currentStatus) {
        super("Analysis already resolved: " + analysisId + " (status: " + currentStatus + ")");
        this.analysisId = analysisId;
        this.currentStatus = currentStatus;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }
}
