package com.platform.analyzer.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Structured output contract for the AI Analyzer.
 * Extended in Phase 15 with additive MCP intelligence fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysis(
        String podName,
        String namespace,
        String verdict,
        String rootCauseAnalysis,
        List<String> recommendedActions,
        List<String> mcpToolsUsed,
        boolean mcpContextAvailable
) {
    /**
     * Backward-compatible constructor — preserves existing 5-arg call sites.
     * Defaults mcpToolsUsed to empty list, mcpContextAvailable to false.
     */
    public AiAnalysis(String podName, String namespace, String verdict,
                      String rootCauseAnalysis, List<String> recommendedActions) {
        this(podName, namespace, verdict, rootCauseAnalysis,
             recommendedActions, List.of(), false);
    }
}
