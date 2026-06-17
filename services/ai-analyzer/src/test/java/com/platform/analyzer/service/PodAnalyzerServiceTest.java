package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiAnalysisRepositoryPort;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import com.platform.analyzer.domain.ports.CircuitBreakerStatePort;
import com.platform.analyzer.domain.ports.McpContextPort;
import com.platform.analyzer.domain.ports.PipelineTracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PodAnalyzerServiceTest {

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

    /** Mock MCP port that always returns EMPTY context (backward-compatible behavior). */
    private static final McpContextPort EMPTY_MCP_CONTEXT = (podName, namespace) -> EnrichedContext.EMPTY;

    /** No-op pipeline tracer for unit tests. */
    private static final PipelineTracer NO_OP_TRACER = new PipelineTracer() {
        @Override public void logCycleStart(String correlationId, String cbState, String podName, String namespace) {}
        @Override public void logToolResult(String correlationId, String toolName, long responseTimeMs, boolean success) {}
        @Override public void logCycleComplete(String correlationId, String podName, String namespace, int toolsUsed, long totalTimeMs, String verdict) {}
        @Override public void logThresholdExceeded(String correlationId, long elapsedMs) {}
    };

    /** Stub circuit breaker state port that always returns CLOSED. */
    private static final CircuitBreakerStatePort CB_STATE_PORT = () -> "CLOSED";

    @Test
    @DisplayName("should delegate to AiLanguageModelPort and return result")
    void shouldDelegateToPort() {
        AiAnalysis expected = new AiAnalysis(
                "payment-service-7d9f8b-xkp2q", "production", "CRITICAL_FAILURE",
                "OOMKilled", List.of("Increase memory limits"));

        AiLanguageModelPort mockPort = (event, history, context) -> expected;
        PodAnalyzerService service = new PodAnalyzerService(mockPort, EMPTY_REPO, EMPTY_MCP_CONTEXT, NO_OP_TRACER, CB_STATE_PORT);

        AiAnalysis result = service.analyse(FAILED_POD_EVENT);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should propagate exception from port")
    void shouldPropagateException() {
        AiLanguageModelPort failingPort = (event, history, context) -> {
            throw new com.platform.analyzer.domain.ports.AiAnalysisException("AI failed");
        };
        PodAnalyzerService service = new PodAnalyzerService(failingPort, EMPTY_REPO, EMPTY_MCP_CONTEXT, NO_OP_TRACER, CB_STATE_PORT);

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

        AiLanguageModelPort portCapturingHistory = (event, history, context) -> {
            assertThat(history).hasSize(1);
            assertThat(history.get(0).verdict()).isEqualTo("TRANSIENT_ISSUE");
            return expected;
        };

        PodAnalyzerService service = new PodAnalyzerService(portCapturingHistory, repoWithHistory, EMPTY_MCP_CONTEXT, NO_OP_TRACER, CB_STATE_PORT);
        AiAnalysis result = service.analyse(FAILED_POD_EVENT);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should pass enriched MCP context to AI language model port")
    void shouldPassMcpContextToPort() {
        EnrichedContext enrichedContext = new EnrichedContext(
                "pod description", "pod events", "pod logs",
                List.of("describe_pod", "get_events", "get_logs"));

        McpContextPort mcpPort = (podName, namespace) -> enrichedContext;

        AiAnalysis expected = new AiAnalysis(
                "payment-service-7d9f8b-xkp2q", "production", "CRITICAL_FAILURE",
                "OOMKilled", List.of("Increase memory limits"));

        AiLanguageModelPort portCapturingContext = (event, history, context) -> {
            assertThat(context).isEqualTo(enrichedContext);
            assertThat(context.hasContent()).isTrue();
            assertThat(context.toolsUsed()).containsExactly("describe_pod", "get_events", "get_logs");
            return expected;
        };

        PodAnalyzerService service = new PodAnalyzerService(portCapturingContext, EMPTY_REPO, mcpPort, NO_OP_TRACER, CB_STATE_PORT);
        AiAnalysis result = service.analyse(FAILED_POD_EVENT);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should pass EnrichedContext.EMPTY to port when MCP returns empty context")
    void shouldPassEmptyContextWhenMcpReturnsEmpty() {
        AiAnalysis expected = new AiAnalysis(
                "payment-service-7d9f8b-xkp2q", "production", "TRANSIENT_ISSUE",
                "Scheduling delay", List.of("Wait for rescheduling"));

        AiLanguageModelPort portCapturingContext = (event, history, context) -> {
            assertThat(context).isSameAs(EnrichedContext.EMPTY);
            assertThat(context.hasContent()).isFalse();
            assertThat(context.toolsUsed()).isEmpty();
            return expected;
        };

        PodAnalyzerService service = new PodAnalyzerService(portCapturingContext, EMPTY_REPO, EMPTY_MCP_CONTEXT, NO_OP_TRACER, CB_STATE_PORT);
        AiAnalysis result = service.analyse(FAILED_POD_EVENT);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should pass partial enrichment context when only some MCP tools succeed")
    void shouldPassPartialEnrichmentContext() {
        // Only pod description available (get_events and get_logs failed)
        EnrichedContext partialContext = new EnrichedContext(
                "pod description with containers info", null, null,
                List.of("describe_pod"));

        McpContextPort partialMcpPort = (podName, namespace) -> partialContext;

        AiAnalysis expected = new AiAnalysis(
                "payment-service-7d9f8b-xkp2q", "production", "CRITICAL_FAILURE",
                "CrashLoopBackOff", List.of("Check image tag"));

        AiLanguageModelPort portCapturingContext = (event, history, context) -> {
            assertThat(context).isEqualTo(partialContext);
            assertThat(context.hasContent()).isTrue();
            assertThat(context.podDescription()).isEqualTo("pod description with containers info");
            assertThat(context.podEvents()).isNull();
            assertThat(context.podLogs()).isNull();
            assertThat(context.toolsUsed()).containsExactly("describe_pod");
            return expected;
        };

        PodAnalyzerService service = new PodAnalyzerService(portCapturingContext, EMPTY_REPO, partialMcpPort, NO_OP_TRACER, CB_STATE_PORT);
        AiAnalysis result = service.analyse(FAILED_POD_EVENT);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should pass partial enrichment context when two MCP tools succeed")
    void shouldPassPartialEnrichmentWithTwoTools() {
        // Pod description and events available, but logs failed
        EnrichedContext partialContext = new EnrichedContext(
                "pod description", "Warning: Back-off restarting", null,
                List.of("describe_pod", "get_events"));

        McpContextPort partialMcpPort = (podName, namespace) -> partialContext;

        AiAnalysis expected = new AiAnalysis(
                "payment-service-7d9f8b-xkp2q", "production", "CRITICAL_FAILURE",
                "CrashLoopBackOff", List.of("Check application startup"));

        AiLanguageModelPort portCapturingContext = (event, history, context) -> {
            assertThat(context).isEqualTo(partialContext);
            assertThat(context.hasContent()).isTrue();
            assertThat(context.podDescription()).isNotNull();
            assertThat(context.podEvents()).isNotNull();
            assertThat(context.podLogs()).isNull();
            assertThat(context.toolsUsed()).containsExactly("describe_pod", "get_events");
            return expected;
        };

        PodAnalyzerService service = new PodAnalyzerService(portCapturingContext, EMPTY_REPO, partialMcpPort, NO_OP_TRACER, CB_STATE_PORT);
        AiAnalysis result = service.analyse(FAILED_POD_EVENT);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should propagate exception when McpContextPort throws RuntimeException")
    void shouldPropagateExceptionFromMcpContextPort() {
        McpContextPort failingMcpPort = (podName, namespace) -> {
            throw new RuntimeException("MCP Server connection refused");
        };

        AiLanguageModelPort neverCalledPort = (event, history, context) -> {
            throw new AssertionError("AI port should not be called when MCP port throws");
        };

        PodAnalyzerService service = new PodAnalyzerService(neverCalledPort, EMPTY_REPO, failingMcpPort, NO_OP_TRACER, CB_STATE_PORT);

        assertThatThrownBy(() -> service.analyse(FAILED_POD_EVENT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MCP Server connection refused");
    }

    @Test
    @DisplayName("should invoke MCP port with correct podName and namespace from event")
    void shouldInvokeMcpPortWithCorrectEventData() {
        AiAnalysis expected = new AiAnalysis(
                "payment-service-7d9f8b-xkp2q", "production", "CRITICAL_FAILURE",
                "OOMKilled", List.of("Increase memory"));

        McpContextPort capturingMcpPort = (podName, namespace) -> {
            assertThat(podName).isEqualTo("payment-service-7d9f8b-xkp2q");
            assertThat(namespace).isEqualTo("production");
            return EnrichedContext.EMPTY;
        };

        AiLanguageModelPort simplePort = (event, history, context) -> expected;

        PodAnalyzerService service = new PodAnalyzerService(simplePort, EMPTY_REPO, capturingMcpPort, NO_OP_TRACER, CB_STATE_PORT);
        AiAnalysis result = service.analyse(FAILED_POD_EVENT);

        assertThat(result).isEqualTo(expected);
    }
}
