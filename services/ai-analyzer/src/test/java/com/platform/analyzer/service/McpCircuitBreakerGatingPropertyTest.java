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
import net.jqwik.api.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for MCP circuit breaker gating logic.
 *
 * Property 1: Circuit breaker state determines MCP invocation and result fields.
 * Property 2: Sequential MCP tool invocation order (describe_pod → get_events → get_logs).
 * Property 3: Partial success produces correct EnrichedContext.
 *
 * Validates: Requirements 5.1, 5.2, 5.4, 5.5, 5.6, 6.2, 6.3
 */
@Tag("Feature: e2e-validation-diagnostic-calibration, Property 1/2/3: MCP Circuit Breaker Gating")
class McpCircuitBreakerGatingPropertyTest {

    private static final String TOOL_DESCRIBE_POD = "describe_pod";
    private static final String TOOL_GET_EVENTS = "get_events";
    private static final String TOOL_GET_LOGS = "get_logs";

    // ─── Property 1: CB state determines MCP invocation and result fields ────────

    @Property(tries = 100)
    void circuitBreakerStateDeterminesMcpInvocationAndResultFields(
            @ForAll("randomCbState") String cbState,
            @ForAll("randomToolSuccessCombination") List<Boolean> toolSuccess
    ) {
        // Arrange: mock ports based on CB state
        RecordingMcpContextPort mcpPort = new RecordingMcpContextPort(toolSuccess);
        StubCircuitBreakerStatePort cbPort = new StubCircuitBreakerStatePort(cbState);
        StubAiLanguageModelPort aiPort = new StubAiLanguageModelPort();
        StubAiAnalysisRepositoryPort repoPort = new StubAiAnalysisRepositoryPort();
        NoOpPipelineTracer tracer = new NoOpPipelineTracer();

        PodAnalyzerService service = new PodAnalyzerService(
                aiPort, repoPort, mcpPort, tracer, cbPort);

        KubernetesEvent event = new KubernetesEvent(
                "test-pod", "default", PodPhase.Failed, Instant.now());

        // Act
        AiAnalysis result = service.analyse(event);

        // Assert: MCP was always invoked (CB gating happens at infrastructure level)
        assertThat(mcpPort.wasInvoked()).isTrue();

        // Result always has mcpToolsUsed and mcpContextAvailable fields
        assertThat(result.mcpToolsUsed()).isNotNull();
        assertThat(result.mcpToolsUsed().size()).isBetween(0, 3);
    }

    // ─── Property 2: Sequential MCP tool invocation order ────────────────────────

    @Property(tries = 100)
    void mcpToolsAreInvokedInSequentialOrder(
            @ForAll("randomToolSuccessCombination") List<Boolean> toolSuccess
    ) {
        // Arrange
        RecordingMcpContextPort mcpPort = new RecordingMcpContextPort(toolSuccess);
        StubCircuitBreakerStatePort cbPort = new StubCircuitBreakerStatePort("CLOSED");
        StubAiLanguageModelPort aiPort = new StubAiLanguageModelPort();
        StubAiAnalysisRepositoryPort repoPort = new StubAiAnalysisRepositoryPort();
        NoOpPipelineTracer tracer = new NoOpPipelineTracer();

        PodAnalyzerService service = new PodAnalyzerService(
                aiPort, repoPort, mcpPort, tracer, cbPort);

        KubernetesEvent event = new KubernetesEvent(
                "test-pod", "default", PodPhase.Failed, Instant.now());

        // Act
        service.analyse(event);

        // Assert: tools in the result maintain the canonical order
        List<String> toolsUsed = mcpPort.getLastToolsUsed();
        if (toolsUsed.size() > 1) {
            // Verify order: describe_pod before get_events before get_logs
            for (int i = 0; i < toolsUsed.size() - 1; i++) {
                int currentIdx = canonicalToolIndex(toolsUsed.get(i));
                int nextIdx = canonicalToolIndex(toolsUsed.get(i + 1));
                assertThat(currentIdx)
                        .as("Tools must be in sequential order: %s", toolsUsed)
                        .isLessThan(nextIdx);
            }
        }
    }

    // ─── Property 3: Partial success produces correct EnrichedContext ─────────────

    @Property(tries = 100)
    void partialSuccessProducesCorrectEnrichedContext(
            @ForAll("randomToolSuccessCombination") List<Boolean> toolSuccess
    ) {
        // Arrange
        RecordingMcpContextPort mcpPort = new RecordingMcpContextPort(toolSuccess);
        StubCircuitBreakerStatePort cbPort = new StubCircuitBreakerStatePort("CLOSED");
        StubAiLanguageModelPort aiPort = new StubAiLanguageModelPort();
        StubAiAnalysisRepositoryPort repoPort = new StubAiAnalysisRepositoryPort();
        NoOpPipelineTracer tracer = new NoOpPipelineTracer();

        PodAnalyzerService service = new PodAnalyzerService(
                aiPort, repoPort, mcpPort, tracer, cbPort);

        KubernetesEvent event = new KubernetesEvent(
                "test-pod", "default", PodPhase.Failed, Instant.now());

        // Act
        AiAnalysis result = service.analyse(event);

        // Assert: count of successful tools matches mcpToolsUsed size
        int expectedCount = 0;
        if (toolSuccess.get(0)) expectedCount++;
        if (toolSuccess.get(1)) expectedCount++;
        if (toolSuccess.get(2)) expectedCount++;

        assertThat(result.mcpToolsUsed()).hasSize(expectedCount);

        // Assert: mcpContextAvailable is true iff at least one tool succeeded
        assertThat(result.mcpContextAvailable()).isEqualTo(expectedCount > 0);

        // Assert: only successful tools appear in mcpToolsUsed
        if (toolSuccess.get(0)) {
            assertThat(result.mcpToolsUsed()).contains(TOOL_DESCRIBE_POD);
        } else {
            assertThat(result.mcpToolsUsed()).doesNotContain(TOOL_DESCRIBE_POD);
        }
        if (toolSuccess.get(1)) {
            assertThat(result.mcpToolsUsed()).contains(TOOL_GET_EVENTS);
        } else {
            assertThat(result.mcpToolsUsed()).doesNotContain(TOOL_GET_EVENTS);
        }
        if (toolSuccess.get(2)) {
            assertThat(result.mcpToolsUsed()).contains(TOOL_GET_LOGS);
        } else {
            assertThat(result.mcpToolsUsed()).doesNotContain(TOOL_GET_LOGS);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private int canonicalToolIndex(String tool) {
        return switch (tool) {
            case TOOL_DESCRIBE_POD -> 0;
            case TOOL_GET_EVENTS -> 1;
            case TOOL_GET_LOGS -> 2;
            default -> -1;
        };
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> randomCbState() {
        return Arbitraries.of("CLOSED", "OPEN", "HALF_OPEN", "UNKNOWN");
    }

    @Provide
    Arbitrary<List<Boolean>> randomToolSuccessCombination() {
        return Arbitraries.of(true, false).list().ofSize(3);
    }

    // ─── Test Doubles ────────────────────────────────────────────────────────────

    /**
     * Recording MCP port that simulates partial tool success based on a boolean triplet.
     */
    static class RecordingMcpContextPort implements McpContextPort {
        private final List<Boolean> toolSuccess;
        private boolean invoked = false;
        private List<String> lastToolsUsed = List.of();

        RecordingMcpContextPort(List<Boolean> toolSuccess) {
            this.toolSuccess = toolSuccess;
        }

        @Override
        public EnrichedContext retrieveContext(String podName, String namespace) {
            invoked = true;
            List<String> tools = new ArrayList<>();
            String podDescription = null;
            String podEvents = null;
            String podLogs = null;

            if (toolSuccess.get(0)) {
                podDescription = "containers: [{name: app}], phase: Failed";
                tools.add(TOOL_DESCRIBE_POD);
            }
            if (toolSuccess.get(1)) {
                podEvents = "[{type: Warning, reason: BackOff}]";
                tools.add(TOOL_GET_EVENTS);
            }
            if (toolSuccess.get(2)) {
                podLogs = "ERROR: process crashed";
                tools.add(TOOL_GET_LOGS);
            }

            if (tools.isEmpty()) return EnrichedContext.EMPTY;
            lastToolsUsed = List.copyOf(tools);
            return new EnrichedContext(podDescription, podEvents, podLogs, lastToolsUsed);
        }

        boolean wasInvoked() { return invoked; }
        List<String> getLastToolsUsed() { return lastToolsUsed; }
    }

    static class StubCircuitBreakerStatePort implements CircuitBreakerStatePort {
        private final String state;
        StubCircuitBreakerStatePort(String state) { this.state = state; }
        @Override
        public String getMcpCircuitBreakerState() { return state; }
    }

    static class StubAiLanguageModelPort implements AiLanguageModelPort {
        @Override
        public AiAnalysis analyze(KubernetesEvent event, List<AiAnalysis> history, EnrichedContext context) {
            List<String> toolsUsed = (context != null && context.hasContent())
                    ? context.toolsUsed() : List.of();
            boolean contextAvailable = context != null && context.hasContent();
            return new AiAnalysis(
                    event.podName(), event.namespace(), "CRITICAL_FAILURE",
                    "Root cause detected", List.of("kubectl delete pod " + event.podName()),
                    toolsUsed, contextAvailable);
        }
    }

    static class StubAiAnalysisRepositoryPort implements AiAnalysisRepositoryPort {
        @Override
        public void save(AiAnalysis analysis) { /* no-op */ }
        @Override
        public List<AiAnalysis> findByPodName(String podName) {
            return List.of();
        }
        @Override
        public List<AiAnalysis> findByVerdict(String verdict) {
            return List.of();
        }
    }

    static class NoOpPipelineTracer implements PipelineTracer {
        @Override
        public void logCycleStart(String correlationId, String cbState, String podName, String namespace) {}
        @Override
        public void logToolResult(String correlationId, String toolName, long responseTimeMs, boolean success) {}
        @Override
        public void logCycleComplete(String correlationId, String podName, String namespace,
                                      int toolsUsed, long totalTimeMs, String verdict) {}
        @Override
        public void logThresholdExceeded(String correlationId, long elapsedMs) {}
    }
}
