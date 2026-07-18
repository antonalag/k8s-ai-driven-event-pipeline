package com.platform.analyzer.domain.model;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for AuditLogPage domain value object.
 *
 * Property 3: Pagination Metadata Consistency
 * For any valid query with page p and size s against a dataset of N resolved documents,
 * the returned result SHALL satisfy: totalElements == N, totalPages == ceil(N / s),
 * page == p, and content.size() <= s.
 *
 * Validates: Requirements 1.7, 2.6, 4.1, 4.5, 4.6, 4.7
 */
class AuditLogPageProperties {

    // ─── Property 3: Pagination Metadata Consistency ─────────────────────────────

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 3: Pagination metadata consistency — totalElements matches dataset size")
    void totalElementsMatchesDatasetSize(
            @ForAll @IntRange(min = 0, max = 50) int datasetSize,
            @ForAll @IntRange(min = 0, max = 10) int page,
            @ForAll @IntRange(min = 1, max = 100) int size) {

        List<AuditLogEntry> allEntries = generateEntries(datasetSize);
        int totalPages = (int) Math.ceil((double) datasetSize / size);
        List<AuditLogEntry> pageContent = getPageContent(allEntries, page, size);

        AuditLogPage auditLogPage = new AuditLogPage(pageContent, datasetSize, page, totalPages);

        assertThat(auditLogPage.totalElements()).isEqualTo(datasetSize);
    }

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 3: Pagination metadata consistency — totalPages equals ceil(N/s)")
    void totalPagesEqualsCeilDivision(
            @ForAll @IntRange(min = 0, max = 50) int datasetSize,
            @ForAll @IntRange(min = 0, max = 10) int page,
            @ForAll @IntRange(min = 1, max = 100) int size) {

        List<AuditLogEntry> allEntries = generateEntries(datasetSize);
        int expectedTotalPages = (int) Math.ceil((double) datasetSize / size);
        List<AuditLogEntry> pageContent = getPageContent(allEntries, page, size);

        AuditLogPage auditLogPage = new AuditLogPage(pageContent, datasetSize, page, expectedTotalPages);

        assertThat(auditLogPage.totalPages()).isEqualTo(expectedTotalPages);
    }

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 3: Pagination metadata consistency — page equals requested page")
    void pageEqualsRequestedPage(
            @ForAll @IntRange(min = 0, max = 10) int page,
            @ForAll @IntRange(min = 1, max = 100) int size,
            @ForAll @IntRange(min = 0, max = 50) int datasetSize) {

        List<AuditLogEntry> allEntries = generateEntries(datasetSize);
        int totalPages = (int) Math.ceil((double) datasetSize / size);
        List<AuditLogEntry> pageContent = getPageContent(allEntries, page, size);

        AuditLogPage auditLogPage = new AuditLogPage(pageContent, datasetSize, page, totalPages);

        assertThat(auditLogPage.page()).isEqualTo(page);
    }

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 3: Pagination metadata consistency — content size is at most page size")
    void contentSizeIsAtMostPageSize(
            @ForAll @IntRange(min = 0, max = 50) int datasetSize,
            @ForAll @IntRange(min = 0, max = 10) int page,
            @ForAll @IntRange(min = 1, max = 100) int size) {

        List<AuditLogEntry> allEntries = generateEntries(datasetSize);
        int totalPages = (int) Math.ceil((double) datasetSize / size);
        List<AuditLogEntry> pageContent = getPageContent(allEntries, page, size);

        AuditLogPage auditLogPage = new AuditLogPage(pageContent, datasetSize, page, totalPages);

        assertThat(auditLogPage.content().size()).isLessThanOrEqualTo(size);
    }

    // ─── Construction Invariants ─────────────────────────────────────────────────

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 3: Construction invariant — null content defaults to empty list")
    void nullContentDefaultsToEmptyList(
            @ForAll @IntRange(min = 0, max = 100) int page,
            @ForAll @IntRange(min = 0, max = 50) int totalPages) {

        AuditLogPage auditLogPage = new AuditLogPage(null, 0, page, totalPages);

        assertThat(auditLogPage.content()).isNotNull();
        assertThat(auditLogPage.content()).isEmpty();
    }

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 3: Construction invariant — negative totalElements throws IllegalArgumentException")
    void negativeTotalElementsThrows(
            @ForAll @IntRange(min = -100, max = -1) int negativeTotalElements,
            @ForAll @IntRange(min = 0, max = 10) int page,
            @ForAll @IntRange(min = 0, max = 10) int totalPages) {

        assertThatThrownBy(() -> new AuditLogPage(List.of(), negativeTotalElements, page, totalPages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalElements must be >= 0");
    }

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 3: Construction invariant — negative page throws IllegalArgumentException")
    void negativePageThrows(
            @ForAll @IntRange(min = -100, max = -1) int negativePage,
            @ForAll @IntRange(min = 0, max = 10) int totalPages) {

        assertThatThrownBy(() -> new AuditLogPage(List.of(), 0, negativePage, totalPages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must be >= 0");
    }

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 3: Construction invariant — negative totalPages throws IllegalArgumentException")
    void negativeTotalPagesThrows(
            @ForAll @IntRange(min = -100, max = -1) int negativeTotalPages,
            @ForAll @IntRange(min = 0, max = 10) int page) {

        assertThatThrownBy(() -> new AuditLogPage(List.of(), 0, page, negativeTotalPages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalPages must be >= 0");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private List<AuditLogEntry> generateEntries(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new AuditLogEntry(
                        Instant.now().minusSeconds(i * 60L),
                        "pod-" + i,
                        "namespace-" + (i % 3),
                        "CRITICAL_FAILURE",
                        i % 2 == 0 ? AnalysisStatus.REMEDIATED : AnalysisStatus.DISMISSED,
                        "Root cause " + i,
                        List.of("action-" + i),
                        "llama3.1:8b"))
                .toList();
    }

    private List<AuditLogEntry> getPageContent(List<AuditLogEntry> allEntries, int page, int size) {
        int start = page * size;
        if (start >= allEntries.size()) {
            return List.of();
        }
        int end = Math.min(start + size, allEntries.size());
        return allEntries.subList(start, end);
    }
}
