package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.model.AiAnalysisView;

import java.time.Instant;
import java.util.List;

/**
 * API response DTO for AI analysis query results.
 * Maps 1:1 from {@link AiAnalysisView} — decouples the web layer
 * from the domain read model.
 */
public record AiAnalysisResponse(
        String podName,
        String namespace,
        String verdict,
        String rootCauseAnalysis,
        List<String> recommendedActions,
        Instant analyzedAt,
        String modelUsed
) {

    /**
     * Factory method that maps an {@link AiAnalysisView} to this DTO
     * preserving all fields without transformation.
     */
    public static AiAnalysisResponse from(AiAnalysisView view) {
        return new AiAnalysisResponse(
                view.podName(),
                view.namespace(),
                view.verdict(),
                view.rootCauseAnalysis(),
                view.recommendedActions(),
                view.analyzedAt(),
                view.modelUsed()
        );
    }
}
