package com.platform.analyzer.model;

import java.time.Instant;
import java.util.List;

/**
 * Outbound event payload published to the {@code ai-analysis-events} Kafka topic.
 *
 * <p>This record is the serialised form of a completed AI diagnosis. It extends the
 * fields defined in {@code specs/schemas/ai-analysis.v1.json} with pipeline metadata
 * ({@code analyzedAt}, {@code sourceEventTimestamp}) to give downstream consumers
 * full observability over the analysis lifecycle.
 *
 * <p>Field mapping to {@code ai-analysis.v1.json}:
 * <ul>
 *   <li>{@code podName}             → {@code podName} (required, minLength 1)</li>
 *   <li>{@code namespace}           → {@code namespace} (required, minLength 1)</li>
 *   <li>{@code verdict}             → {@code verdict} (enum: HEALTHY | TRANSIENT_ISSUE | CRITICAL_FAILURE)</li>
 *   <li>{@code rootCauseAnalysis}   → {@code rootCauseAnalysis} (required, max 500 chars)</li>
 *   <li>{@code recommendedActions}  → {@code recommendedActions} (1–10 items)</li>
 * </ul>
 *
 * @param podName              The name of the analysed Kubernetes Pod.
 * @param namespace            The Kubernetes namespace of the analysed Pod.
 * @param verdict              AI health classification for the Pod.
 * @param rootCauseAnalysis    Concise root-cause explanation produced by the AI agent.
 * @param recommendedActions   Ordered list of concrete mitigation steps.
 * @param analyzedAt           UTC instant at which the AI analysis was completed.
 * @param sourceEventTimestamp UTC instant of the original {@link KubernetesEvent} that triggered the analysis.
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
    /**
     * Factory method that builds an {@link AiAnalysisEvent} from a parsed {@link AiAnalysis}
     * and the originating {@link KubernetesEvent}, stamping the current UTC time as
     * {@code analyzedAt}.
     *
     * @param analysis the structured AI diagnosis
     * @param source   the original Kubernetes event that triggered the analysis
     * @return a fully populated {@link AiAnalysisEvent} ready for Kafka publication
     */
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
