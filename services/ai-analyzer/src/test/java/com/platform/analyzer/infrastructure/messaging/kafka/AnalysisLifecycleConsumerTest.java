package com.platform.analyzer.infrastructure.messaging.kafka;

import com.platform.analyzer.domain.model.AnalysisLifecycleEvent;
import com.platform.analyzer.domain.ports.AnalysisLifecycleRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AnalysisLifecycleConsumer}.
 * Requirements: 7.1, 7.3
 */
@ExtendWith(MockitoExtension.class)
class AnalysisLifecycleConsumerTest {

    @Mock
    private AnalysisLifecycleRepositoryPort repository;

    private AnalysisLifecycleConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AnalysisLifecycleConsumer(repository);
    }

    @Test
    @DisplayName("onLifecycleEvent() with LIFECYCLE_CHANGE calls updateStatus on repository")
    void lifecycleChangeEventCallsUpdateStatus() {
        Instant now = Instant.now();
        AnalysisLifecycleEvent event = new AnalysisLifecycleEvent(
                "doc-123", "crash-pod", "production",
                "PENDING", "DISMISSED", "False positive",
                now, "LIFECYCLE_CHANGE");

        consumer.onLifecycleEvent(event);

        verify(repository).updateStatus("doc-123", "DISMISSED", now.toString(), "False positive");
    }

    @Test
    @DisplayName("onLifecycleEvent() skips non-lifecycle events without calling repository")
    void nonLifecycleEventIsSkipped() {
        AnalysisLifecycleEvent event = new AnalysisLifecycleEvent(
                "doc-456", "some-pod", "default",
                "PENDING", "DISMISSED", "reason",
                Instant.now(), "SOME_OTHER_TYPE");

        consumer.onLifecycleEvent(event);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("onLifecycleEvent() skips event with null eventType without calling repository")
    void nullEventTypeIsSkipped() {
        AnalysisLifecycleEvent event = new AnalysisLifecycleEvent(
                "doc-789", "pod-x", "ns-1",
                "PENDING", "DISMISSED", "reason",
                Instant.now(), null);

        consumer.onLifecycleEvent(event);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("onLifecycleEvent() catches repository exception and does not propagate it")
    void repositoryExceptionIsCaughtAndSwallowed() {
        Instant now = Instant.now();
        AnalysisLifecycleEvent event = new AnalysisLifecycleEvent(
                "doc-missing", "pod-y", "ns-2",
                "PENDING", "DISMISSED", "reason",
                now, "LIFECYCLE_CHANGE");

        doThrow(new RuntimeException("Document not found"))
                .when(repository).updateStatus(anyString(), anyString(), anyString(), anyString());

        // Should NOT throw — exception is caught internally
        consumer.onLifecycleEvent(event);

        verify(repository).updateStatus("doc-missing", "DISMISSED", now.toString(), "reason");
    }
}
