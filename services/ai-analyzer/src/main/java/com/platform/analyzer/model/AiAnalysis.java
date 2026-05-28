package com.platform.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Structured output contract for the AI Analyzer.
 *
 * <p>This record is the Java representation of {@code specs/schemas/ai-analysis.v1.json}.
 * Every field is required — the AI model is instructed to always populate all of them.
 * {@code additionalProperties} is set to false in the schema, so unknown fields are
 * silently ignored here to be defensive against minor model deviations.
 *
 * @param podName             The Pod name echoed back from the input event.
 * @param namespace           The namespace echoed back from the input event.
 * @param verdict             Health classification: HEALTHY, TRANSIENT_ISSUE, or CRITICAL_FAILURE.
 * @param rootCauseAnalysis   Concise root-cause explanation (max 500 chars).
 * @param recommendedActions  Ordered list of concrete mitigation steps (1–10 items).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysis(
        String podName,
        String namespace,
        String verdict,
        String rootCauseAnalysis,
        List<String> recommendedActions
) {}
