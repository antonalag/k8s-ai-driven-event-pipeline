package com.platform.analyzer.domain.model;

import java.time.Instant;

/**
 * Domain event record published when an analysis lifecycle state changes.
 * Sent to the {@code ai-analysis-events} Kafka topic for downstream consumers.
 *
 * <p>Zero Spring imports — pure domain.</p>
 */
public record AnalysisLifecycleEvent(
        String analysisId,
        String podName,
        String namespace,
        String previousStatus,
        String newStatus,
        String resolutionReason,
        Instant resolvedAt,
        String eventType
) {

    /**
     * Static factory for constructing a dismissal lifecycle event
     * from the current state of an {@link AnalysisLifecycle} entity.
     */
    public static AnalysisLifecycleEvent dismissed(AnalysisLifecycle lifecycle) {
        return new AnalysisLifecycleEvent(
                lifecycle.getId(),
                lifecycle.getAnalysis().podName(),
                lifecycle.getAnalysis().namespace(),
                "PENDING",
                "DISMISSED",
                lifecycle.getResolutionReason(),
                Instant.now(),
                "LIFECYCLE_CHANGE"
        );
    }
}
