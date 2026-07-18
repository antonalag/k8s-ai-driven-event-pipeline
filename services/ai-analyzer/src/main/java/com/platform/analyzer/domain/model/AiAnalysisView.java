package com.platform.analyzer.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Read model for AI analysis query results.
 * Enriches AiAnalysis with infrastructure metadata (analyzedAt)
 * without contaminating the core domain model.
 */
public record AiAnalysisView(
        String podName,
        String namespace,
        String verdict,
        String rootCauseAnalysis,
        List<String> recommendedActions,
        Instant analyzedAt,
        String modelUsed
) {}
