package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.*;
import com.platform.analyzer.domain.ports.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DismissAnalysisService}.
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5
 */
@ExtendWith(MockitoExtension.class)
class DismissAnalysisServiceTest {

    private static final String ANALYSIS_ID = "crash-pod-abc-1720000000000";
    private static final AiAnalysis SAMPLE_ANALYSIS = new AiAnalysis(
            "crash-pod-abc", "production", "CrashLoopBackOff",
            "Container exits with code 1", List.of("kubectl rollout restart"));

    @Mock
    private AnalysisLifecycleRepositoryPort repoPort;

    @Mock
    private LifecycleMessagingPort messagingPort;

    private DismissAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new DismissAnalysisService(repoPort, messagingPort);
    }

    @Test
    @DisplayName("dismiss() happy path — transitions to DISMISSED, saves, publishes event, returns result")
    void dismissHappyPath() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle(ANALYSIS_ID, SAMPLE_ANALYSIS);
        when(repoPort.findById(ANALYSIS_ID)).thenReturn(Optional.of(lifecycle));

        DismissalResult result = service.dismiss(ANALYSIS_ID, "False positive");

        assertThat(result.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(result.newStatus()).isEqualTo(AnalysisStatus.DISMISSED);

        verify(repoPort).save(lifecycle);
        verify(messagingPort).publishLifecycleEvent(any(AnalysisLifecycleEvent.class));
    }

    @Test
    @DisplayName("dismiss() publishes event with correct fields")
    void dismissPublishesCorrectEvent() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle(ANALYSIS_ID, SAMPLE_ANALYSIS);
        when(repoPort.findById(ANALYSIS_ID)).thenReturn(Optional.of(lifecycle));

        service.dismiss(ANALYSIS_ID, "Resolved itself");

        ArgumentCaptor<AnalysisLifecycleEvent> captor = ArgumentCaptor.forClass(AnalysisLifecycleEvent.class);
        verify(messagingPort).publishLifecycleEvent(captor.capture());

        AnalysisLifecycleEvent event = captor.getValue();
        assertThat(event.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(event.podName()).isEqualTo("crash-pod-abc");
        assertThat(event.namespace()).isEqualTo("production");
        assertThat(event.previousStatus()).isEqualTo("PENDING");
        assertThat(event.newStatus()).isEqualTo("DISMISSED");
        assertThat(event.eventType()).isEqualTo("LIFECYCLE_CHANGE");
        assertThat(event.resolutionReason()).isEqualTo("Resolved itself");
        assertThat(event.resolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("dismiss() with null reason sets default 'Dismissed by operator'")
    void dismissNullReasonSetsDefault() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle(ANALYSIS_ID, SAMPLE_ANALYSIS);
        when(repoPort.findById(ANALYSIS_ID)).thenReturn(Optional.of(lifecycle));

        service.dismiss(ANALYSIS_ID, null);

        assertThat(lifecycle.getResolutionReason()).isEqualTo("Dismissed by operator");
    }

    @Test
    @DisplayName("dismiss() throws AnalysisNotFoundException when ID not found")
    void dismissNotFoundThrows() {
        when(repoPort.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismiss("nonexistent", "reason"))
                .isInstanceOf(AnalysisNotFoundException.class)
                .hasMessageContaining("nonexistent");

        verify(repoPort, never()).save(any());
        verify(messagingPort, never()).publishLifecycleEvent(any());
    }

    @Test
    @DisplayName("dismiss() throws AnalysisAlreadyResolvedException when DISMISSED")
    void dismissAlreadyDismissedThrows() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle(ANALYSIS_ID, SAMPLE_ANALYSIS,
                AnalysisStatus.DISMISSED, LocalDateTime.now(), "previously dismissed");
        when(repoPort.findById(ANALYSIS_ID)).thenReturn(Optional.of(lifecycle));

        assertThatThrownBy(() -> service.dismiss(ANALYSIS_ID, "try again"))
                .isInstanceOf(AnalysisAlreadyResolvedException.class)
                .hasMessageContaining("DISMISSED");

        verify(repoPort, never()).save(any());
        verify(messagingPort, never()).publishLifecycleEvent(any());
    }

    @Test
    @DisplayName("dismiss() throws AnalysisAlreadyResolvedException when REMEDIATED")
    void dismissRemediatedThrows() {
        AnalysisLifecycle lifecycle = new AnalysisLifecycle(ANALYSIS_ID, SAMPLE_ANALYSIS,
                AnalysisStatus.REMEDIATED, LocalDateTime.now(), "auto-remediated");
        when(repoPort.findById(ANALYSIS_ID)).thenReturn(Optional.of(lifecycle));

        assertThatThrownBy(() -> service.dismiss(ANALYSIS_ID, "try dismiss"))
                .isInstanceOf(AnalysisAlreadyResolvedException.class)
                .hasMessageContaining("REMEDIATED");

        verify(repoPort, never()).save(any());
        verify(messagingPort, never()).publishLifecycleEvent(any());
    }
}
