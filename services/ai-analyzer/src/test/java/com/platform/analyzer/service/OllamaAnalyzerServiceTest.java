package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OllamaAnalyzerService — verifies orchestration via port interface.
 */
class OllamaAnalyzerServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-15T10:30:00Z");

    private static final KubernetesEvent FAILED_POD_EVENT = new KubernetesEvent(
            "payment-service-7d9f8b-xkp2q",
            "production",
            PodPhase.Failed,
            FIXED_INSTANT
    );

    @Test
    @DisplayName("should delegate to AiLanguageModelPort and return result")
    void shouldDelegateToPort() {
        AiAnalysis expected = new AiAnalysis(
                "payment-service-7d9f8b-xkp2q", "production", "CRITICAL_FAILURE",
                "OOMKilled", List.of("Increase memory limits"));

        AiLanguageModelPort mockPort = event -> expected;
        OllamaAnalyzerService service = new OllamaAnalyzerService(mockPort);

        AiAnalysis result = service.analyse(FAILED_POD_EVENT);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should propagate exception from port")
    void shouldPropagateException() {
        AiLanguageModelPort failingPort = event -> {
            throw new com.platform.analyzer.domain.ports.AiAnalysisException("AI failed");
        };
        OllamaAnalyzerService service = new OllamaAnalyzerService(failingPort);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.analyse(FAILED_POD_EVENT))
                .isInstanceOf(com.platform.analyzer.domain.ports.AiAnalysisException.class)
                .hasMessageContaining("AI failed");
    }
}
