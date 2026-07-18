package com.platform.analyzer.infrastructure.web.dto;

import com.platform.analyzer.domain.model.AnalysisStatus;
import com.platform.analyzer.domain.model.AuditLogEntry;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for HistoryResponseDto structural completeness.
 *
 * Validates: Requirements 2.3
 */
class HistoryResponseDtoProperties {

    // ─── Property 7: DTO Structural Completeness ─────────────────────────────────

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 7: DTO structural completeness — all 8 fields non-null")
    void mappingFromEntryProducesAllNonNullFields(@ForAll("validAuditLogEntry") AuditLogEntry entry) {
        HistoryResponseDto dto = HistoryResponseDto.from(entry);

        assertThat(dto.resolvedAt()).isNotNull();
        assertThat(dto.podName()).isNotNull();
        assertThat(dto.namespace()).isNotNull();
        assertThat(dto.verdict()).isNotNull();
        assertThat(dto.status()).isNotNull();
        assertThat(dto.rootCauseAnalysis()).isNotNull();
        assertThat(dto.recommendedActions()).isNotNull();
        assertThat(dto.modelUsed()).isNotNull();
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<AuditLogEntry> validAuditLogEntry() {
        Arbitrary<Instant> resolvedAt = Arbitraries.longs()
                .between(0, Instant.now().getEpochSecond())
                .map(Instant::ofEpochSecond);
        Arbitrary<String> podName = Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> namespace = Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(30);
        Arbitrary<String> verdict = Arbitraries.of(
                "CrashLoopBackOff", "OOMKilled", "ImagePullBackOff", "CRITICAL_FAILURE");
        Arbitrary<AnalysisStatus> status = Arbitraries.of(
                AnalysisStatus.REMEDIATED, AnalysisStatus.DISMISSED);
        Arbitrary<String> rootCause = Arbitraries.strings()
                .alpha().numeric().withChars(' ', '.', ',')
                .ofMinLength(1).ofMaxLength(200);
        Arbitrary<List<String>> actions = Arbitraries.strings()
                .alpha().numeric().withChars(' ', '-')
                .ofMinLength(3).ofMaxLength(50)
                .list().ofMinSize(0).ofMaxSize(5);
        Arbitrary<String> modelUsed = Arbitraries.of(
                "llama3.1:8b", "gpt-4o", "claude-3.5-sonnet", "unknown");

        return Combinators.combine(resolvedAt, podName, namespace, verdict, status, rootCause, actions, modelUsed)
                .as(AuditLogEntry::new);
    }
}
