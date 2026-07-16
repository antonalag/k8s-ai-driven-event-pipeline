package com.platform.analyzer.infrastructure.persistence.opensearch;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.AiAnalysisView;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Field(type = FieldType.Keyword)
    private List<String> mcpToolsUsed;

    @Field(type = FieldType.Boolean)
    private boolean mcpContextAvailable;

    @Field(type = FieldType.Object)
    private Map<String, String> labels;

    @Field(type = FieldType.Date)
    private Instant analyzedAt;

    @Field(type = FieldType.Date)
    private Instant sourceEventTimestamp;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private Instant resolvedAt;

    @Field(type = FieldType.Text)
    private String resolutionReason;

    public AiAnalysisDocument() {}

    public static AiAnalysisDocument from(AiAnalysis analysis) {
        AiAnalysisDocument doc = new AiAnalysisDocument();
        Instant now = Instant.now();
        doc.analyzedAt = now;
        doc.id = analysis.podName() + "-" + now.toEpochMilli();
        doc.podName = analysis.podName();
        doc.namespace = analysis.namespace();
        doc.verdict = analysis.verdict();
        doc.rootCauseAnalysis = analysis.rootCauseAnalysis();
        doc.recommendedActions = analysis.recommendedActions();
        doc.mcpToolsUsed = analysis.mcpToolsUsed() != null ? analysis.mcpToolsUsed() : List.of();
        doc.mcpContextAvailable = analysis.mcpContextAvailable();
        doc.labels = new HashMap<>();
        doc.status = "PENDING";
        return doc;
    }

    public AiAnalysis toDomain() {
        return new AiAnalysis(podName, namespace, verdict, rootCauseAnalysis, recommendedActions,
                mcpToolsUsed != null ? mcpToolsUsed : List.of(), mcpContextAvailable);
    }

    public AiAnalysisView toView() {
        return new AiAnalysisView(podName, namespace, verdict, rootCauseAnalysis, recommendedActions, analyzedAt);
    }

    public String getId() { return id; }
    public String getPodName() { return podName; }
    public String getNamespace() { return namespace; }
    public String getVerdict() { return verdict; }
    public String getRootCauseAnalysis() { return rootCauseAnalysis; }
    public List<String> getRecommendedActions() { return recommendedActions; }
    public List<String> getMcpToolsUsed() { return mcpToolsUsed; }
    public boolean isMcpContextAvailable() { return mcpContextAvailable; }
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public Instant getSourceEventTimestamp() { return sourceEventTimestamp; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolutionReason() { return resolutionReason; }
    public void setResolutionReason(String resolutionReason) { this.resolutionReason = resolutionReason; }
}
