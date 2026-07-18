package com.platform.analyzer.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Structured output contract for the AI Analyzer.
 * Extended in Phase 15 with additive MCP intelligence fields.
 * Extended with modelUsed tracking for audit log history.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysis(
        String podName,
        String namespace,
        String verdict,
        String rootCauseAnalysis,
        List<String> recommendedActions,
        List<String> mcpToolsUsed,
        boolean mcpContextAvailable,
        String modelUsed
) {
    /**
     * Compact-canonical constructor — validates and defaults nullable fields.
     */
    public AiAnalysis {
        if (modelUsed == null || modelUsed.isBlank()) modelUsed = "unknown";
        if (mcpToolsUsed == null) mcpToolsUsed = List.of();
    }

    /**
     * Backward-compatible 7-arg constructor — preserves existing call sites.
     * Defaults modelUsed to "unknown".
     */
    public AiAnalysis(String podName, String namespace, String verdict,
                      String rootCauseAnalysis, List<String> recommendedActions,
                      List<String> mcpToolsUsed, boolean mcpContextAvailable) {
        this(podName, namespace, verdict, rootCauseAnalysis,
             recommendedActions, mcpToolsUsed, mcpContextAvailable, "unknown");
    }

    /**
     * Backward-compatible 5-arg constructor — preserves legacy call sites.
     * Defaults mcpToolsUsed to empty list, mcpContextAvailable to false, modelUsed to "unknown".
     */
    public AiAnalysis(String podName, String namespace, String verdict,
                      String rootCauseAnalysis, List<String> recommendedActions) {
        this(podName, namespace, verdict, rootCauseAnalysis,
             recommendedActions, List.of(), false, "unknown");
    }
}
