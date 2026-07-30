package com.platform.analyzer.infrastructure.adapter.outbound.persistence;

import com.platform.analyzer.domain.model.valueobject.AiAnalysis;
import com.platform.analyzer.domain.model.valueobject.AiAnalysisView;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for query exclusion of dismissed/remediated analyses.
 * Feature: analysis-dismissal
 *
 * Property 6: Query Excludes Dismissed and Remediated Analyses
 * For any set of documents with mixed statuses, query returns only active documents
 * (non-DISMISSED, non-REMEDIATED, and non-excluded verdicts).
 *
 * Validates: Requirements 7.2
 */
class AnalysisQueryFilterProperties {

    @Property(tries = 100)
    @Label("Feature: analysis-dismissal, Property 6: Query excludes dismissed and remediated analyses")
    void findAllNeverReturnsDismissedOrRemediatedDocuments(
            @ForAll @IntRange(min = 0, max = 20) int pendingCount,
            @ForAll @IntRange(min = 0, max = 20) int dismissedCount,
            @ForAll @IntRange(min = 0, max = 10) int remediatedCount,
            @ForAll @IntRange(min = 0, max = 5) int nullStatusCount) {

        // Create documents with mixed statuses — each with a unique deployment prefix
        // to avoid deduplication collapsing results
        List<AiAnalysisDocument> allDocs = new ArrayList<>();

        for (int i = 0; i < pendingCount; i++) {
            allDocs.add(createDocumentWithUniqueDeployment("pending" + i, "PENDING", "CrashLoopBackOff"));
        }
        for (int i = 0; i < dismissedCount; i++) {
            allDocs.add(createDocumentWithUniqueDeployment("dismissed" + i, "DISMISSED", "CrashLoopBackOff"));
        }
        for (int i = 0; i < remediatedCount; i++) {
            allDocs.add(createDocumentWithUniqueDeployment("remediated" + i, "REMEDIATED", "CrashLoopBackOff"));
        }
        for (int i = 0; i < nullStatusCount; i++) {
            allDocs.add(createDocumentWithUniqueDeployment("nullstatus" + i, null, "CrashLoopBackOff"));
        }

        // Mock repository
        SpringDataAiAnalysisRepository repository = mock(SpringDataAiAnalysisRepository.class);
        when(repository.findAll()).thenReturn(allDocs);

        OpenSearchAnalysisQueryAdapter adapter = new OpenSearchAnalysisQueryAdapter(repository);

        // Act
        List<AiAnalysisView> results = adapter.findAll();

        // Assert — only PENDING and null-status documents pass the filter
        // (DISMISSED and REMEDIATED are excluded by EXCLUDED_STATUSES)
        int expectedCount = pendingCount + nullStatusCount;
        assertThat(results).hasSize(expectedCount);

        // Verify no dismissed or remediated pod names in results
        List<String> resultPodNames = results.stream()
                .map(AiAnalysisView::podName)
                .toList();
        for (int i = 0; i < dismissedCount; i++) {
            assertThat(resultPodNames).doesNotContain("dismissed" + i + "-app-abc123-xyz99");
        }
        for (int i = 0; i < remediatedCount; i++) {
            assertThat(resultPodNames).doesNotContain("remediated" + i + "-app-abc123-xyz99");
        }
    }

    /**
     * Creates a document with a pod name that has a unique deployment prefix.
     * Pod name format: {uniquePrefix}-app-{replicaHash}-{podHash}
     * so extractDeploymentPrefix yields "{uniquePrefix}-app" (unique per document).
     */
    private AiAnalysisDocument createDocumentWithUniqueDeployment(String uniquePrefix, String status, String verdict) {
        String podName = uniquePrefix + "-app-abc123-xyz99";
        AiAnalysis analysis = new AiAnalysis(
                podName, "default", verdict,
                "Test root cause", List.of("restart pod"));
        AiAnalysisDocument doc = AiAnalysisDocument.from(analysis);
        doc.setStatus(status);
        return doc;
    }
}
