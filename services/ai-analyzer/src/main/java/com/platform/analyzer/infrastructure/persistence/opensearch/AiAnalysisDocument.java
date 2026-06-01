package com.platform.analyzer.infrastructure.persistence.opensearch;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.AiAnalysisEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;

/**
 * OpenSearch document entity representing a persisted AI analysis report.
 */
@Document(indexName = "ai-analysis-reports")
public class AiAnalysisDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String podName;

    @Field(type = FieldType.Keyword)
    private String namespace;

    @Field(type = FieldType.Keyword)
    private String verdict;

    @Field(type = FieldType.Text)
    private String rootCauseAnalysis;

    @Field(type = FieldType.Text)
    private List<String> recommendedActions;

    @Field(type = FieldType.Date)
    private Instant analyzedAt;

    @Field(type = FieldType.Date)
    private Instant sourceEventTimestamp;

    public AiAnalysisDocument() {}

    public static AiAnalysisDocument from(AiAnalysis analysis) {
        AiAnalysisDocument doc = new AiAnalysisDocument();
        doc.id = analysis.podName() + "-" + Instant.now().toEpochMilli();
        doc.podName = analysis.podName();
        doc.namespace = analysis.namespace();
        doc.verdict = analysis.verdict();
        doc.rootCauseAnalysis = analysis.rootCauseAnalysis();
        doc.recommendedActions = analysis.recommendedActions();
        doc.analyzedAt = Instant.now();
        return doc;
    }

    public AiAnalysis toDomain() {
        return new AiAnalysis(podName, namespace, verdict, rootCauseAnalysis, recommendedActions);
    }

    public String getId() { return id; }
    public String getPodName() { return podName; }
    public String getNamespace() { return namespace; }
    public String getVerdict() { return verdict; }
    public String getRootCauseAnalysis() { return rootCauseAnalysis; }
    public List<String> getRecommendedActions() { return recommendedActions; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public Instant getSourceEventTimestamp() { return sourceEventTimestamp; }
}
