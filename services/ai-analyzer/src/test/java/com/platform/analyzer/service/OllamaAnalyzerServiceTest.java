package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiAnalysisRepositoryPort;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaAnalyzerServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-15T10:30:00Z");

    private static final KubernetesEvent FAILED_POD_EVENT = new KubernetesEvent(
            "payment-service-7d9f8b-xkp2q",
            "production",
            PodPhase.Failed,
            FIXED_INSTANT
    );

    private static final AiAnalysisRepositoryPort EMPTY_REPO = new AiAnalysisRepositoryPort() {
        @Override public void save(AiAnalysis analysis) {}
        @Override public List<AiAnalysis> findByPodName(String podName) { return List.of(); }
        @Override public List<AiAnalysis> findByVerdict(String verdict) { return List.of(); }
    };

    @Test
    @DisplayName("should delegate to AiLanguageModelPort and return result")
    void shouldDelegateToPort() {
        AiAnalysis expected = new AiAnalysis(
                "payment-service-7d9f8b-xkp2q", "production", "CRITICAL_FAILURE",
                "OOMKilled", List.of("Increase memory limits"));

        AiLanguageModelPort mockPort = (event, history) -> expected;
        OllamaAnalyzerService service = new OllamaAnalyzerService(mockPort, EMPTY_REPO);

        AiAnalysis result = service.analyse(FAILED_POD_EVENT);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should propagate exception from port")
    void shouldPropagateException() {
        AiLanguageModelPort failingPort = (event, history) -> {
            throw new com.platform.analyzer.domain.ports.AiAnalysisException("AI failed");
        };
        OllamaAnalyzerService service = new OllamaAnalyzerService(failingPort, EMPTY_REPO);

        assertThatThrownBy(() -> service.analyse(FAILED_POD_EVENT))
                .isInstanceOf(com.platform.analyzer.domain.ports.AiAnalysisException.class)
                .hasMessageContaining("AI failed");
    }

    @Test
    @DisplayName("should pass history from repository to port")
    void shouldPassHistoryToPort() {
        AiAnalysis previousAnalysis = new AiAnalysis(
                "payment-service-7d9f8b-xkp2q", "production", "TRANSIENT_ISSUE",
                "Scheduling delay", List.of("Wait"));

        AiAnalysisRepositoryPort repoWithHistory = new AiAnalysisRepositoryPort() {
            @Override public void save(AiAnalysis analysis) {}
            @Override public List<AiAnalysis> findByPodName(String podName) {
                return List.of(previousAnalysis);
            }
            @Override public List<AiAnalysis> findByVerdict(String verdict) { return List.of(); }
        };

        AiAnalysis expected = new AiAnalysis(
                "payment-service-7d9f8b-xkp2q", "production", "CRITICAL_FAILURE",
                "OOMKilled after transient", List.of("Increase memory"));

        AiLanguageModelPort portCapturingHistory = (event, history) -> {
            assertThat(history).hasSize(1);
            assertThat(history.get(0).verdict()).isEqualTo("TRANSIENT_ISSUE");
            return expected;
        };

        OllamaAnalyzerService service = new OllamaAnalyzerService(portCapturingHistory, repoWithHistory);
        AiAnalysis result = service.analyse(FAILED_POD_EVENT);

        assertThat(result).isEqualTo(expected);
    }
}
