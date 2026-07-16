package com.platform.analyzer.infrastructure.persistence.opensearch;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.AiAnalysisView;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for query exclusion of dismissed analyses.
 * Feature: analysis-dismissal
 *
 * Property 6: Query Excludes Dismissed Analyses
 * For any set of documents with mixed statuses, query returns only non-DISMISSED documents.
 *
 * Validates: Requirements 7.2
 */
class AnalysisQueryFilterProperties {

    @Property(tries = 100)
    @Label("Feature: analysis-dismissal, Property 6: Query excludes dismissed analyses")
    void findAllNeverReturnsDismissedDocuments(
            @ForAll @IntRange(min = 0, max = 20) int pendingCount,
            @ForAll @IntRange(min = 0, max = 20) int dismissedCount,
            @ForAll @IntRange(min = 0, max = 10) int remediatedCount,
            @ForAll @IntRange(min = 0, max = 5) int nullStatusCount) {

        // Create documents with mixed statuses
        List<AiAnalysisDocument> allDocs = new ArrayList<>();

        for (int i = 0; i < pendingCount; i++) {
            allDocs.add(createDocument("pod-pending-" + i, "PENDING"));
        }
        for (int i = 0; i < dismissedCount; i++) {
            allDocs.add(createDocument("pod-dismissed-" + i, "DISMISSED"));
        }
        for (int i = 0; i < remediatedCount; i++) {
            allDocs.add(createDocument("pod-remediated-" + i, "REMEDIATED"));
        }
        for (int i = 0; i < nullStatusCount; i++) {
            allDocs.add(createDocument("pod-null-" + i, null));
        }

        // Mock repository
        SpringDataAiAnalysisRepository repository = mock(SpringDataAiAnalysisRepository.class);
        when(repository.findAll()).thenReturn(allDocs);

        OpenSearchAnalysisQueryAdapter adapter = new OpenSearchAnalysisQueryAdapter(repository);

        // Act
        List<AiAnalysisView> results = adapter.findAll();

        // Assert — no DISMISSED documents in results
        int expectedCount = pendingCount + remediatedCount + nullStatusCount;
        assertThat(results).hasSize(expectedCount);

        // Verify no dismissed pod names in results
        List<String> resultPodNames = results.stream()
                .map(AiAnalysisView::podName)
                .toList();
        for (int i = 0; i < dismissedCount; i++) {
            assertThat(resultPodNames).doesNotContain("pod-dismissed-" + i);
        }
    }

    private AiAnalysisDocument createDocument(String podName, String status) {
        AiAnalysis analysis = new AiAnalysis(
                podName, "default", "CrashLoopBackOff",
                "Test root cause", List.of("restart pod"));
        AiAnalysisDocument doc = AiAnalysisDocument.from(analysis);
        doc.setStatus(status);
        return doc;
    }
}
