package com.platform.analyzer.domain.model;

import java.time.LocalDateTime;

/**
 * Mutable domain entity wrapping the immutable {@link AiAnalysis} record
 * with lifecycle state tracking.
 */
public class AnalysisLifecycle {

    private final String id;
    private final AiAnalysis analysis;
    private AnalysisStatus status;
    private LocalDateTime resolvedAt;
    private String resolutionReason;

    public AnalysisLifecycle(String id, AiAnalysis analysis) {
        this.id = id;
        this.analysis = analysis;
        this.status = AnalysisStatus.PENDING;
        this.resolvedAt = null;
        this.resolutionReason = null;
    }

    public AnalysisLifecycle(String id, AiAnalysis analysis,
                             AnalysisStatus status,
                             LocalDateTime resolvedAt,
                             String resolutionReason) {
        this.id = id;
        this.analysis = analysis;
        this.status = status;
        this.resolvedAt = resolvedAt;
        this.resolutionReason = resolutionReason;
    }

    /**
     * Transitions to DISMISSED state. Only allowed from PENDING.
     */
    public void dismiss(String reason, LocalDateTime timestamp) {
        if (this.status != AnalysisStatus.PENDING) {
            throw new IllegalStateException(
                "Cannot dismiss analysis in state: " + this.status);
        }
        this.status = AnalysisStatus.DISMISSED;
        this.resolvedAt = timestamp;
        this.resolutionReason = (reason == null || reason.isBlank())
            ? "Dismissed by operator"
            : reason;
    }

    public boolean isResolved() {
        return this.status == AnalysisStatus.DISMISSED
            || this.status == AnalysisStatus.REMEDIATED;
    }

    public String getId() { return id; }
    public AiAnalysis getAnalysis() { return analysis; }
    public AnalysisStatus getStatus() { return status; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public String getResolutionReason() { return resolutionReason; }
}
