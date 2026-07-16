package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.*;
import com.platform.analyzer.domain.ports.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for DismissAnalysisService.
 * Feature: analysis-dismissal
 */
class DismissAnalysisServiceProperties {

    // ─── Property 4: Lifecycle Event Publication ─────────────────────────────────

    /**
     * Validates: Requirements 4.4, 6.1
     */
    @Property(tries = 100)
    @Label("Feature: analysis-dismissal, Property 4: Lifecycle event publication on dismiss")
    void successfulDismissPublishesExactlyOneEventWithCorrectFields(
            @ForAll @NotBlank String analysisId,
            @ForAll("validAnalysis") AiAnalysis analysis,
            @ForAll @NotBlank String reason) {

        // Arrange
        AnalysisLifecycleRepositoryPort repoPort = mock(AnalysisLifecycleRepositoryPort.class);
        LifecycleMessagingPort messagingPort = mock(LifecycleMessagingPort.class);

        AnalysisLifecycle lifecycle = new AnalysisLifecycle(analysisId, analysis);
        when(repoPort.findById(analysisId)).thenReturn(Optional.of(lifecycle));

        DismissAnalysisService service = new DismissAnalysisService(repoPort, messagingPort);

        // Act
        service.dismiss(analysisId, reason);

        // Assert — exactly one event published
        ArgumentCaptor<AnalysisLifecycleEvent> captor = ArgumentCaptor.forClass(AnalysisLifecycleEvent.class);
        verify(messagingPort, times(1)).publishLifecycleEvent(captor.capture());

        AnalysisLifecycleEvent event = captor.getValue();
        assertThat(event.analysisId()).isEqualTo(analysisId);
        assertThat(event.previousStatus()).isEqualTo("PENDING");
        assertThat(event.newStatus()).isEqualTo("DISMISSED");
        assertThat(event.eventType()).isEqualTo("LIFECYCLE_CHANGE");
        assertThat(event.podName()).isEqualTo(analysis.podName());
        assertThat(event.namespace()).isEqualTo(analysis.namespace());
        assertThat(event.resolvedAt()).isNotNull();
    }

    // ─── Property 5: Already-Resolved Rejection ──────────────────────────────────

    /**
     * Validates: Requirements 3.3, 4.5
     */
    @Property(tries = 100)
    @Label("Feature: analysis-dismissal, Property 5: Already-resolved rejection — DISMISSED")
    void dismissOnDismissedThrowsAlreadyResolved(
            @ForAll @NotBlank String analysisId,
            @ForAll("validAnalysis") AiAnalysis analysis,
            @ForAll @NotBlank String reason) {

        AnalysisLifecycleRepositoryPort repoPort = mock(AnalysisLifecycleRepositoryPort.class);
        LifecycleMessagingPort messagingPort = mock(LifecycleMessagingPort.class);

        AnalysisLifecycle lifecycle = new AnalysisLifecycle(analysisId, analysis,
                AnalysisStatus.DISMISSED, LocalDateTime.now(), "already dismissed");
        when(repoPort.findById(analysisId)).thenReturn(Optional.of(lifecycle));

        DismissAnalysisService service = new DismissAnalysisService(repoPort, messagingPort);

        assertThatThrownBy(() -> service.dismiss(analysisId, reason))
                .isInstanceOf(AnalysisAlreadyResolvedException.class);
        verify(messagingPort, never()).publishLifecycleEvent(any());
    }

    /**
     * Validates: Requirements 3.3, 4.5
     */
    @Property(tries = 100)
    @Label("Feature: analysis-dismissal, Property 5: Already-resolved rejection — REMEDIATED")
    void dismissOnRemediatedThrowsAlreadyResolved(
            @ForAll @NotBlank String analysisId,
            @ForAll("validAnalysis") AiAnalysis analysis,
            @ForAll @NotBlank String reason) {

        AnalysisLifecycleRepositoryPort repoPort = mock(AnalysisLifecycleRepositoryPort.class);
        LifecycleMessagingPort messagingPort = mock(LifecycleMessagingPort.class);

        AnalysisLifecycle lifecycle = new AnalysisLifecycle(analysisId, analysis,
                AnalysisStatus.REMEDIATED, LocalDateTime.now(), "auto-fixed");
        when(repoPort.findById(analysisId)).thenReturn(Optional.of(lifecycle));

        DismissAnalysisService service = new DismissAnalysisService(repoPort, messagingPort);

        assertThatThrownBy(() -> service.dismiss(analysisId, reason))
                .isInstanceOf(AnalysisAlreadyResolvedException.class);
        verify(messagingPort, never()).publishLifecycleEvent(any());
    }

    /**
     * Validates: Requirements 3.3, 4.5
     */
    @Property(tries = 100)
    @Label("Feature: analysis-dismissal, Property 5: Not-found rejection")
    void dismissOnNonExistentIdThrowsNotFoundException(
            @ForAll @NotBlank String analysisId,
            @ForAll @NotBlank String reason) {

        AnalysisLifecycleRepositoryPort repoPort = mock(AnalysisLifecycleRepositoryPort.class);
        LifecycleMessagingPort messagingPort = mock(LifecycleMessagingPort.class);

        when(repoPort.findById(analysisId)).thenReturn(Optional.empty());

        DismissAnalysisService service = new DismissAnalysisService(repoPort, messagingPort);

        assertThatThrownBy(() -> service.dismiss(analysisId, reason))
                .isInstanceOf(AnalysisNotFoundException.class);
        verify(messagingPort, never()).publishLifecycleEvent(any());
    }

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
}
