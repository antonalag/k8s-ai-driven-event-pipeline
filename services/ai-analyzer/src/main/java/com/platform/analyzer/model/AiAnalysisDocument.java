package com.platform.analyzer.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;

/**
 * OpenSearch document entity representing a persisted AI analysis report.
 *
 * <p>Maps to the {@code ai-analysis-reports} index. The document ID is composed
 * of {@code {podName}-{analyzedAt}} to ensure idempotent re-indexing: if the same
 * analysis is re-processed (e.g. on consumer restart), the document is overwritten
 * rather than duplicated.
 *
 * <p>Field mapping mirrors {@code specs/schemas/ai-analysis.v1.json} plus pipeline
 * metadata fields ({@code analyzedAt}, {@code sourceEventTimestamp}).
 */
@Document(indexName = "ai-analysis-reports")
public class AiAnalysisDocument {

    /**
     * Composite document ID: {@code {podName}-{analyzedAt}}.
     * Ensures idempotent indexing — re-processing the same event overwrites the document.
     */
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String podName;

    @Field(type = FieldType.Keyword)
    private String namespace;

    /**
     * AI health classification: HEALTHY, TRANSIENT_ISSUE, or CRITICAL_FAILURE.
     * Stored as a keyword for exact-match filtering and aggregations.
     */
    @Field(type = FieldType.Keyword)
    private String verdict;

    /**
     * Concise root-cause explanation produced by the AI agent (max 500 chars).
     * Stored as text to enable full-text search over diagnoses.
     */
    @Field(type = FieldType.Text)
    private String rootCauseAnalysis;

    /**
     * Ordered list of concrete mitigation steps (1–10 items).
     */
    @Field(type = FieldType.Text)
    private List<String> recommendedActions;

    /**
     * UTC instant at which the AI analysis was completed by the analyzer service.
     */
    @Field(type = FieldType.Date)
    private Instant analyzedAt;

    /**
     * UTC instant of the original {@link KubernetesEvent} that triggered the analysis.
     */
    @Field(type = FieldType.Date)
    private Instant sourceEventTimestamp;

    // ── Constructors ──────────────────────────────────────────────────────────

    public AiAnalysisDocument() {}

    /**
     * Factory method that builds an {@link AiAnalysisDocument} from an
     * {@link AiAnalysisEvent}, generating the composite document ID automatically.
     *
     * @param event the outbound AI analysis event consumed from {@code ai-analysis-events}
     * @return a fully populated document ready for indexing
     */
    public static AiAnalysisDocument from(AiAnalysisEvent event) {
        AiAnalysisDocument doc = new AiAnalysisDocument();
        doc.id = event.podName() + "-" + event.analyzedAt().toEpochMilli();
        doc.podName = event.podName();
        doc.namespace = event.namespace();
        doc.verdict = event.verdict();
        doc.rootCauseAnalysis = event.rootCauseAnalysis();
        doc.recommendedActions = event.recommendedActions();
        doc.analyzedAt = event.analyzedAt();
        doc.sourceEventTimestamp = event.sourceEventTimestamp();
        return doc;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getPodName() { return podName; }
    public String getNamespace() { return namespace; }
    public String getVerdict() { return verdict; }
    public String getRootCauseAnalysis() { return rootCauseAnalysis; }
    public List<String> getRecommendedActions() { return recommendedActions; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public Instant getSourceEventTimestamp() { return sourceEventTimestamp; }

    @Override
    public String toString() {
        return "AiAnalysisDocument{id='%s', verdict='%s', pod='%s/%s'}"
                .formatted(id, verdict, namespace, podName);
    }
}
