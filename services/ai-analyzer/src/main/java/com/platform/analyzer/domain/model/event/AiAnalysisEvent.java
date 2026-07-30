package com.platform.analyzer.domain.model.event;

import com.platform.analyzer.domain.model.valueobject.AiAnalysis;
import com.platform.analyzer.domain.model.valueobject.KubernetesEvent;

import java.time.Instant;
import java.util.List;

/**
 * Outbound event payload published to the ai-analysis-events topic.
 * Carries the full analysis contract including MCP intelligence fields.
 */
public record AiAnalysisEvent(
        String podName,
        String namespace,
        String verdict,
        String rootCauseAnalysis,
        List<String> recommendedActions,
        List<String> mcpToolsUsed,
        boolean mcpContextAvailable,
        String modelUsed,
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
                analysis.mcpToolsUsed(),
                analysis.mcpContextAvailable(),
                analysis.modelUsed(),
                Instant.now(),
                source.timestamp()
        );
    }
}
