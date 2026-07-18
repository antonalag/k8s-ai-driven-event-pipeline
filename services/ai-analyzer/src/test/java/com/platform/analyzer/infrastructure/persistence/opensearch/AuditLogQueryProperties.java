package com.platform.analyzer.infrastructure.persistence.opensearch;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.AnalysisStatus;
import com.platform.analyzer.domain.model.AuditLogEntry;
import com.platform.analyzer.domain.model.AuditLogPage;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for OpenSearchAuditLogQueryAdapter query behavior.
 * Feature: audit-log-history
 *
 * Validates: Requirements 1.2, 1.3, 1.4, 1.9
 */
class AuditLogQueryProperties {

    /**
     * Property 1: Status Filter Invariant
     * For any paginated query result from AuditLogQueryPort, every entry in the content list
     * SHALL have a status that is either REMEDIATED or DISMISSED — no PENDING documents shall ever appear.
     *
     * Validates: Requirements 1.2, 1.3
     */
    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 1: Status filter invariant")
    void allReturnedEntriesHaveResolvedStatus(
            @ForAll @IntRange(min = 0, max = 15) int remediatedCount,
            @ForAll @IntRange(min = 0, max = 15) int dismissedCount) {

        // Build documents with only resolved statuses (simulating what OpenSearch returns
        // after filtering by findByStatusIn(["REMEDIATED", "DISMISSED"]))
        List<AiAnalysisDocument> resolvedDocs = new ArrayList<>();

        for (int i = 0; i < remediatedCount; i++) {
            resolvedDocs.add(createDocument("pod-rem-" + i, "REMEDIATED",
                    Instant.now().minusSeconds(i * 60L)));
        }
        for (int i = 0; i < dismissedCount; i++) {
            resolvedDocs.add(createDocument("pod-dis-" + i, "DISMISSED",
                    Instant.now().minusSeconds((remediatedCount + i) * 60L)));
        }

        // Sort descending by resolvedAt (simulating OpenSearch sort)
        resolvedDocs.sort(Comparator.comparing(AiAnalysisDocument::getResolvedAt).reversed());

        int totalElements = resolvedDocs.size();
        int size = 10;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        List<AiAnalysisDocument> pageContent = resolvedDocs.subList(0, Math.min(size, totalElements));

        Page<AiAnalysisDocument> mockPage = new PageImpl<>(pageContent,
                PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "resolvedAt")),
                totalElements);

        SpringDataAiAnalysisRepository repository = mock(SpringDataAiAnalysisRepository.class);
        when(repository.findByStatusIn(eq(List.of("REMEDIATED", "DISMISSED")), any(Pageable.class)))
                .thenReturn(mockPage);

        OpenSearchAuditLogQueryAdapter adapter = new OpenSearchAuditLogQueryAdapter(repository);

        // Act
        AuditLogPage result = adapter.findResolvedAnalyses(0, size);

        // Assert — every entry must have REMEDIATED or DISMISSED status
        assertThat(result.content()).allSatisfy(entry ->
                assertThat(entry.status())
                        .isIn(AnalysisStatus.REMEDIATED, AnalysisStatus.DISMISSED));

        // No PENDING should ever appear
        assertThat(result.content()).noneMatch(entry ->
                entry.status() == AnalysisStatus.PENDING);
    }

    /**
     * Property 2: Descending resolvedAt Sort Invariant
     * For any paginated query result containing two or more entries, for every consecutive pair
     * (entry[i], entry[i+1]), entry[i].resolvedAt SHALL be >= entry[i+1].resolvedAt.
     *
     * Validates: Requirements 1.4
     */
    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 2: Descending resolvedAt sort invariant")
    void resultsAreSortedByResolvedAtDescending(
            @ForAll @IntRange(min = 2, max = 20) int docCount) {

        // Build documents with varied resolvedAt timestamps
        List<AiAnalysisDocument> docs = new ArrayList<>();
        Instant baseTime = Instant.parse("2025-01-01T00:00:00Z");

        for (int i = 0; i < docCount; i++) {
            String status = i % 2 == 0 ? "REMEDIATED" : "DISMISSED";
            // Vary timestamps — some intentionally out of order before sorting
            docs.add(createDocument("pod-" + i, status,
                    baseTime.plusSeconds(i * 37L))); // arbitrary spacing
        }

        // Sort descending by resolvedAt (simulating OpenSearch sort behavior)
        docs.sort(Comparator.comparing(AiAnalysisDocument::getResolvedAt).reversed());

        int size = Math.min(docCount, 10);
        List<AiAnalysisDocument> pageContent = docs.subList(0, size);

        Page<AiAnalysisDocument> mockPage = new PageImpl<>(pageContent,
                PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "resolvedAt")),
                docCount);

        SpringDataAiAnalysisRepository repository = mock(SpringDataAiAnalysisRepository.class);
        when(repository.findByStatusIn(eq(List.of("REMEDIATED", "DISMISSED")), any(Pageable.class)))
                .thenReturn(mockPage);

        OpenSearchAuditLogQueryAdapter adapter = new OpenSearchAuditLogQueryAdapter(repository);

        // Act
        AuditLogPage result = adapter.findResolvedAnalyses(0, size);

        // Assert — consecutive pairs maintain descending order
        List<AuditLogEntry> entries = result.content();
        for (int i = 0; i < entries.size() - 1; i++) {
            Instant current = entries.get(i).resolvedAt();
            Instant next = entries.get(i + 1).resolvedAt();
            assertThat(current).isAfterOrEqualTo(next);
        }
    }

    /**
     * Property 6: Beyond-Range Page Returns Empty Content
     * For any dataset with T total pages, requesting page p >= T SHALL return an empty content list
     * with totalElements and totalPages reflecting the actual dataset counts.
     *
     * Validates: Requirements 1.9
     */
    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 6: Beyond-range page returns empty content")
    void beyondRangePageReturnsEmptyContent(
            @ForAll @IntRange(min = 1, max = 50) int totalElements,
            @ForAll @IntRange(min = 1, max = 10) int size) {

        int totalPages = (int) Math.ceil((double) totalElements / size);
        // Request a page beyond the available range
        int requestedPage = totalPages; // first page that is out of range (0-indexed)

        // When requesting beyond range, repository returns empty page with correct totals
        Page<AiAnalysisDocument> emptyPage = new PageImpl<>(
                List.of(),
                PageRequest.of(requestedPage, size, Sort.by(Sort.Direction.DESC, "resolvedAt")),
                totalElements);

        SpringDataAiAnalysisRepository repository = mock(SpringDataAiAnalysisRepository.class);
        when(repository.findByStatusIn(eq(List.of("REMEDIATED", "DISMISSED")), any(Pageable.class)))
                .thenReturn(emptyPage);

        OpenSearchAuditLogQueryAdapter adapter = new OpenSearchAuditLogQueryAdapter(repository);

        // Act
        AuditLogPage result = adapter.findResolvedAnalyses(requestedPage, size);

        // Assert — empty content, but metadata reflects actual dataset
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(totalElements);
        assertThat(result.totalPages()).isEqualTo(totalPages);
        assertThat(result.page()).isEqualTo(requestedPage);
    }

    private AiAnalysisDocument createDocument(String podName, String status, Instant resolvedAt) {
        AiAnalysis analysis = new AiAnalysis(
                podName, "default", "CrashLoopBackOff",
                "Root cause for " + podName, List.of("restart pod"));
        AiAnalysisDocument doc = AiAnalysisDocument.from(analysis);
        doc.setStatus(status);
        doc.setResolvedAt(resolvedAt);
        return doc;
    }
}
