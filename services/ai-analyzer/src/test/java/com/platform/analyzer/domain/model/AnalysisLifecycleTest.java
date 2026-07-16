package com.platform.analyzer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AnalysisLifecycle} domain entity.
 */
class AnalysisLifecycleTest {

    private static final AiAnalysis SAMPLE_ANALYSIS = new AiAnalysis(
            "crash-pod-abc", "production", "CrashLoopBackOff",
            "Container exits with code 1", List.of("kubectl rollout restart"));

    @Test
    @DisplayName("Construction with default-args yields PENDING status and null resolved fields")
    void constructionDefaults() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle("doc-123", SAMPLE_ANALYSIS);

        assertThat(lifecycle.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(lifecycle.getResolvedAt()).isNull();
        assertThat(lifecycle.getResolutionReason()).isNull();
        assertThat(lifecycle.getId()).isEqualTo("doc-123");
        assertThat(lifecycle.getAnalysis()).isSameAs(SAMPLE_ANALYSIS);
    }

    @Test
    @DisplayName("Full-args constructor rehydrates all fields correctly")
    void fullArgsConstructor() {
        LocalDateTime resolved = LocalDateTime.of(2024, 7, 3, 14, 30);
        AnalysisLifecycle lifecycle = new AnalysisLifecycle(
                "doc-456", SAMPLE_ANALYSIS, AnalysisStatus.DISMISSED, resolved, "Test reason");

        assertThat(lifecycle.getStatus()).isEqualTo(AnalysisStatus.DISMISSED);
        assertThat(lifecycle.getResolvedAt()).isEqualTo(resolved);
        assertThat(lifecycle.getResolutionReason()).isEqualTo("Test reason");
    }

    @Test
    @DisplayName("dismiss() transitions PENDING → DISMISSED with correct timestamp and reason")
    void dismissHappyPath() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle("doc-789", SAMPLE_ANALYSIS);
        LocalDateTime now = LocalDateTime.now();

        lifecycle.dismiss("False positive", now);

        assertThat(lifecycle.getStatus()).isEqualTo(AnalysisStatus.DISMISSED);
        assertThat(lifecycle.getResolvedAt()).isEqualTo(now);
        assertThat(lifecycle.getResolutionReason()).isEqualTo("False positive");
    }

    @Test
    @DisplayName("dismiss() with null reason defaults to 'Dismissed by operator'")
    void dismissNullReasonDefaults() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle("doc-1", SAMPLE_ANALYSIS);

        lifecycle.dismiss(null, LocalDateTime.now());

        assertThat(lifecycle.getResolutionReason()).isEqualTo("Dismissed by operator");
    }

    @Test
    @DisplayName("dismiss() with blank reason defaults to 'Dismissed by operator'")
    void dismissBlankReasonDefaults() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle("doc-2", SAMPLE_ANALYSIS);

        lifecycle.dismiss("   ", LocalDateTime.now());

        assertThat(lifecycle.getResolutionReason()).isEqualTo("Dismissed by operator");
    }

    @Test
    @DisplayName("isResolved() returns false for PENDING, true for DISMISSED and REMEDIATED")
    void isResolvedBehavior() {
        AnalysisLifecycle pending = new AnalysisLifecycle("id-1", SAMPLE_ANALYSIS);
        AnalysisLifecycle dismissed = new AnalysisLifecycle("id-2", SAMPLE_ANALYSIS,
                AnalysisStatus.DISMISSED, LocalDateTime.now(), "reason");
        AnalysisLifecycle remediated = new AnalysisLifecycle("id-3", SAMPLE_ANALYSIS,
                AnalysisStatus.REMEDIATED, LocalDateTime.now(), "fixed");

        assertThat(pending.isResolved()).isFalse();
        assertThat(dismissed.isResolved()).isTrue();
        assertThat(remediated.isResolved()).isTrue();
    }

    @Test
    @DisplayName("dismiss() on already DISMISSED throws IllegalStateException")
    void doubleDismissThrows() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle("doc-x", SAMPLE_ANALYSIS);
        lifecycle.dismiss("first", LocalDateTime.now());

        assertThatThrownBy(() -> lifecycle.dismiss("second", LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISMISSED");
    }

    @Test
    @DisplayName("dismiss() on REMEDIATED throws IllegalStateException")
    void dismissOnRemediatedThrows() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle("doc-y", SAMPLE_ANALYSIS,
                AnalysisStatus.REMEDIATED, LocalDateTime.now(), "auto-fixed");

        assertThatThrownBy(() -> lifecycle.dismiss("try dismiss", LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REMEDIATED");
    }
}
