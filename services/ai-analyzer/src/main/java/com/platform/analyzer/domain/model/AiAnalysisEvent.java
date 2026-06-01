package com.platform.analyzer.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Outbound event payload published to the ai-analysis-events topic.
 */
public record AiAnalysisEvent(
        String podName,
        String namespace,
        String verdict,
        String rootCauseAnalysis,
        List<String> recommendedActions,
        Instant analyzedAt,
        Instant sourceEventTimestamp
) {
    public static AiAnalysisEvent from(AiAnalysis analysis, KubernetesEvent source) {
        return new AiAnalysisEvent(
                analysis.podName(),
                analysis.namespace(),
                analysis.verdict(),
                analysis.rootCauseAnalysis(),
                analysis.recommendedActions(),
                Instant.now(),
                source.timestamp()
        );
    }
}
