package com.platform.analyzer.domain.model;

import java.time.Instant;

/**
 * Domain event record published when an analysis lifecycle state changes.
 * Sent to the {@code ai-analysis-events} Kafka topic for downstream consumers.
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
