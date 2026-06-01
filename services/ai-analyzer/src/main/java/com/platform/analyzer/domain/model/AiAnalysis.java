package com.platform.analyzer.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Structured output contract for the AI Analyzer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysis(
        String podName,
        String namespace,
        String verdict,
        String rootCauseAnalysis,
        List<String> recommendedActions
) {}
