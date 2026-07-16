package com.platform.analyzer.domain.model;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AnalysisLifecycle entity.
 * Feature: analysis-dismissal
 */
class AnalysisLifecycleProperties {

    // ─── Property 1: Construction Invariants ─────────────────────────────────────

    @Property(tries = 100)
    @Label("Feature: analysis-dismissal, Property 1: Construction invariants")
    void constructionYieldsPendingWithNullResolvedFields(
            @ForAll @NotBlank String id,
            @ForAll("validAnalysis") AiAnalysis analysis) {

        AnalysisLifecycle lifecycle = new AnalysisLifecycle(id, analysis);

        assertThat(lifecycle.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(lifecycle.getResolvedAt()).isNull();
        assertThat(lifecycle.getResolutionReason()).isNull();
        assertThat(lifecycle.getId()).isEqualTo(id);
        assertThat(lifecycle.getAnalysis()).isSameAs(analysis);
    }

    /**
     * Validates: Requirements 1.2, 2.4, 2.5
     */

    // ─── Property 2: Dismiss State Transition ────────────────────────────────────

    @Property(tries = 100)
    @Label("Feature: analysis-dismissal, Property 2: Dismiss state transition")
    void dismissTransitionsPendingToDismissedWithNonNullResolvedAt(
            @ForAll @NotBlank String id,
            @ForAll("validAnalysis") AiAnalysis analysis,
            @ForAll @NotBlank String reason) {

        AnalysisLifecycle lifecycle = new AnalysisLifecycle(id, analysis);
        LocalDateTime timestamp = LocalDateTime.now();

        lifecycle.dismiss(reason, timestamp);

        assertThat(lifecycle.getStatus()).isEqualTo(AnalysisStatus.DISMISSED);
        assertThat(lifecycle.getResolvedAt()).isNotNull();
        assertThat(lifecycle.getResolvedAt()).isEqualTo(timestamp);
    }

    /**
     * Validates: Requirements 4.1, 4.2
     */

    // ─── Property 3: Resolution Reason Defaulting ────────────────────────────────

    @Property(tries = 100)
    @Label("Feature: analysis-dismissal, Property 3: Resolution reason defaulting — null/blank defaults")
    void nullOrBlankReasonDefaultsToDismissedByOperator(
            @ForAll @NotBlank String id,
            @ForAll("validAnalysis") AiAnalysis analysis,
            @ForAll("nullOrBlankReason") String reason) {

        AnalysisLifecycle lifecycle = new AnalysisLifecycle(id, analysis);
        lifecycle.dismiss(reason, LocalDateTime.now());

        assertThat(lifecycle.getResolutionReason()).isEqualTo("Dismissed by operator");
    }

    @Property(tries = 100)
    @Label("Feature: analysis-dismissal, Property 3: Resolution reason defaulting — explicit reason preserved")
    void explicitReasonIsPreservedExactly(
            @ForAll @NotBlank String id,
            @ForAll("validAnalysis") AiAnalysis analysis,
            @ForAll @NotBlank String reason) {

        AnalysisLifecycle lifecycle = new AnalysisLifecycle(id, analysis);
        lifecycle.dismiss(reason, LocalDateTime.now());

        assertThat(lifecycle.getResolutionReason()).isEqualTo(reason);
    }

    /**
     * Validates: Requirements 4.3
     */

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<AiAnalysis> validAnalysis() {
        Arbitrary<String> podName = Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> namespace = Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(30);
        Arbitrary<String> verdict = Arbitraries.of(
                "CrashLoopBackOff", "OOMKilled", "ImagePullBackOff", "HEALTHY", "DEGRADED");
        Arbitrary<String> rootCause = Arbitraries.strings()
                .alpha().numeric().withChars(' ', '.', ',')
                .ofMinLength(1).ofMaxLength(200);
        Arbitrary<List<String>> actions = Arbitraries.strings()
                .alpha().numeric().withChars(' ', '-')
                .ofMinLength(3).ofMaxLength(50)
                .list().ofMinSize(0).ofMaxSize(3);

        return Combinators.combine(podName, namespace, verdict, rootCause, actions)
                .as(AiAnalysis::new);
    }

    @Provide
    Arbitrary<String> nullOrBlankReason() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }
}
