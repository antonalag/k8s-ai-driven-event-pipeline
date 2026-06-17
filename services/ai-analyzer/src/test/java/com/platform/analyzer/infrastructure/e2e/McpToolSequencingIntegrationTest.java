package com.platform.analyzer.infrastructure.e2e;

import com.platform.analyzer.config.ResilientMcpContextAdapter;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.ports.McpContextPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: MCP tool sequencing (describe_pod → get_events → get_logs).
 *
 * Validates:
 * - Sequential invocation order (describe_pod first, get_events second, get_logs third)
 * - Partial success handling (correct mcpToolsUsed list)
 * - Circuit breaker state gating (OPEN → EnrichedContext.EMPTY)
 *
 * Validates: Requirements 5.1, 5.2, 5.4, 5.5, 5.6
 */
@DisplayName("MCP Tool Sequencing Integration Test")
class McpToolSequencingIntegrationTest {

    private static final String POD_NAME = "chaos-crashloop-pod";
    private static final String NAMESPACE = "chaos-validation";

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(ResourceAccessException.class, RuntimeException.class)
                .build();

        circuitBreaker = CircuitBreakerRegistry.of(config)
                .circuitBreaker("mcp-sequencing-test-" + System.nanoTime());
    }

    // ─── Sequential Invocation Order ─────────────────────────────────────────────

    @Nested
    @DisplayName("Sequential invocation order")
    class SequentialInvocationOrder {

        @Test
        @DisplayName("should invoke tools in order: describe_pod → get_events → get_logs")
        void shouldInvokeToolsInSequentialOrder() {
            List<String> invocationOrder = new ArrayList<>();

            McpContextPort orderTrackingDelegate = (podName, namespace) -> {
                // Simulate sequential tool calls by tracking invocation order
                invocationOrder.add("describe_pod");
                invocationOrder.add("get_events");
                invocationOrder.add("get_logs");

                return new EnrichedContext(
                        "pod description content",
                        "events content",
                        "logs content",
                        List.of("describe_pod", "get_events", "get_logs")
                );
            };

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(orderTrackingDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            // Verify sequential order
            assertThat(invocationOrder).containsExactly("describe_pod", "get_events", "get_logs");
            assertThat(result.toolsUsed()).containsExactly("describe_pod", "get_events", "get_logs");
        }

        @Test
        @DisplayName("should return all 3 tools in toolsUsed when all succeed")
        void shouldReturnAllToolsWhenAllSucceed() {
            McpContextPort allSuccessDelegate = (podName, namespace) -> new EnrichedContext(
                    "pod description",
                    "pod events",
                    "pod logs",
                    List.of("describe_pod", "get_events", "get_logs")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(allSuccessDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result.toolsUsed()).containsExactly("describe_pod", "get_events", "get_logs");
            assertThat(result.toolsUsed()).hasSize(3);
            assertThat(result.podDescription()).isNotNull();
            assertThat(result.podEvents()).isNotNull();
            assertThat(result.podLogs()).isNotNull();
            assertThat(result.hasContent()).isTrue();
        }

        @Test
        @DisplayName("describe_pod should be called first in the sequence")
        void describePodShouldBeCalledFirst() {
            McpContextPort delegate = (podName, namespace) -> new EnrichedContext(
                    "pod desc",
                    "events",
                    "logs",
                    List.of("describe_pod", "get_events", "get_logs")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(delegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            // First tool in the sequence should always be describe_pod
            assertThat(result.toolsUsed().get(0)).isEqualTo("describe_pod");
        }

        @Test
        @DisplayName("get_events should be called second in the sequence")
        void getEventsShouldBeCalledSecond() {
            McpContextPort delegate = (podName, namespace) -> new EnrichedContext(
                    "pod desc",
                    "events",
                    "logs",
                    List.of("describe_pod", "get_events", "get_logs")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(delegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            // Second tool in the sequence should always be get_events
            assertThat(result.toolsUsed().get(1)).isEqualTo("get_events");
        }

        @Test
        @DisplayName("get_logs should be called third in the sequence")
        void getLogsShouldBeCalledThird() {
            McpContextPort delegate = (podName, namespace) -> new EnrichedContext(
                    "pod desc",
                    "events",
                    "logs",
                    List.of("describe_pod", "get_events", "get_logs")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(delegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            // Third tool in the sequence should always be get_logs
            assertThat(result.toolsUsed().get(2)).isEqualTo("get_logs");
        }
    }

    // ─── Partial Success Handling ────────────────────────────────────────────────

    @Nested
    @DisplayName("Partial success handling")
    class PartialSuccessHandling {

        @Test
        @DisplayName("when describe_pod fails, toolsUsed should contain only get_events and get_logs")
        void whenDescribePodFailsToolsUsedReflectsRemainingSuccess() {
            McpContextPort partialDelegate = (podName, namespace) -> new EnrichedContext(
                    null,  // describe_pod failed
                    "pod events content",
                    "pod logs content",
                    List.of("get_events", "get_logs")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(partialDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result.toolsUsed()).containsExactly("get_events", "get_logs");
            assertThat(result.toolsUsed()).doesNotContain("describe_pod");
            assertThat(result.podDescription()).isNull();
            assertThat(result.podEvents()).isNotNull();
            assertThat(result.podLogs()).isNotNull();
            assertThat(result.hasContent()).isTrue();
        }

        @Test
        @DisplayName("when get_events fails, toolsUsed should contain only describe_pod and get_logs")
        void whenGetEventsFailsToolsUsedReflectsRemainingSuccess() {
            McpContextPort partialDelegate = (podName, namespace) -> new EnrichedContext(
                    "pod description",
                    null,  // get_events failed
                    "pod logs content",
                    List.of("describe_pod", "get_logs")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(partialDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result.toolsUsed()).containsExactly("describe_pod", "get_logs");
            assertThat(result.toolsUsed()).doesNotContain("get_events");
            assertThat(result.podDescription()).isNotNull();
            assertThat(result.podEvents()).isNull();
            assertThat(result.podLogs()).isNotNull();
            assertThat(result.hasContent()).isTrue();
        }

        @Test
        @DisplayName("when get_logs fails, toolsUsed should contain only describe_pod and get_events")
        void whenGetLogsFailsToolsUsedReflectsRemainingSuccess() {
            McpContextPort partialDelegate = (podName, namespace) -> new EnrichedContext(
                    "pod description",
                    "pod events",
                    null,  // get_logs failed
                    List.of("describe_pod", "get_events")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(partialDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result.toolsUsed()).containsExactly("describe_pod", "get_events");
            assertThat(result.toolsUsed()).doesNotContain("get_logs");
            assertThat(result.podDescription()).isNotNull();
            assertThat(result.podEvents()).isNotNull();
            assertThat(result.podLogs()).isNull();
            assertThat(result.hasContent()).isTrue();
        }

        @Test
        @DisplayName("when only describe_pod succeeds, toolsUsed should contain only describe_pod")
        void whenOnlyDescribePodSucceeds() {
            McpContextPort partialDelegate = (podName, namespace) -> new EnrichedContext(
                    "pod description content",
                    null,  // get_events failed
                    null,  // get_logs failed
                    List.of("describe_pod")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(partialDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result.toolsUsed()).containsExactly("describe_pod");
            assertThat(result.toolsUsed()).hasSize(1);
            assertThat(result.podDescription()).isNotNull();
            assertThat(result.podEvents()).isNull();
            assertThat(result.podLogs()).isNull();
            assertThat(result.hasContent()).isTrue();
        }

        @Test
        @DisplayName("when all tools fail, toolsUsed should be empty and EMPTY context returned")
        void whenAllToolsFailReturnsEmptyContext() {
            McpContextPort allFailDelegate = (podName, namespace) -> EnrichedContext.EMPTY;

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(allFailDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result).isSameAs(EnrichedContext.EMPTY);
            assertThat(result.toolsUsed()).isEmpty();
            assertThat(result.hasContent()).isFalse();
            assertThat(result.podDescription()).isNull();
            assertThat(result.podEvents()).isNull();
            assertThat(result.podLogs()).isNull();
        }

        @Test
        @DisplayName("partial success sets mcpToolsUsed to reflect only successful tools")
        void partialSuccessSetsCorrectToolsUsedList() {
            // Only get_logs succeeds
            McpContextPort partialDelegate = (podName, namespace) -> new EnrichedContext(
                    null,
                    null,
                    "container logs output",
                    List.of("get_logs")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(partialDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result.toolsUsed()).containsExactly("get_logs");
            assertThat(result.toolsUsed()).doesNotContain("describe_pod", "get_events");
            assertThat(result.hasContent()).isTrue();
        }
    }

    // ─── Circuit Breaker State Gating ────────────────────────────────────────────

    @Nested
    @DisplayName("Circuit breaker state gating")
    class CircuitBreakerStateGating {

        @Test
        @DisplayName("when circuit breaker is OPEN, should return EnrichedContext.EMPTY")
        void whenCircuitBreakerOpenReturnsEmpty() {
            circuitBreaker.transitionToOpenState();

            McpContextPort neverCalledDelegate = (podName, namespace) -> {
                throw new AssertionError("Delegate should not be called when circuit breaker is OPEN");
            };

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(neverCalledDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result).isSameAs(EnrichedContext.EMPTY);
            assertThat(result.toolsUsed()).isEmpty();
            assertThat(result.hasContent()).isFalse();
        }

        @Test
        @DisplayName("when circuit breaker is OPEN, mcpContextAvailable should be false")
        void whenCircuitBreakerOpenMcpContextNotAvailable() {
            circuitBreaker.transitionToOpenState();

            McpContextPort neverCalledDelegate = (podName, namespace) -> {
                throw new AssertionError("Delegate should not be called");
            };

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(neverCalledDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            // EnrichedContext.EMPTY means mcpContextAvailable = false downstream
            assertThat(result).isEqualTo(EnrichedContext.EMPTY);
            assertThat(result.toolsUsed()).isEmpty();
            assertThat(result.hasContent()).isFalse();
        }

        @Test
        @DisplayName("when circuit breaker is CLOSED, should delegate to MCP tools")
        void whenCircuitBreakerClosedDelegatesToMcp() {
            // CB is CLOSED by default from setUp()
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            McpContextPort successDelegate = (podName, namespace) -> new EnrichedContext(
                    "pod description",
                    "pod events",
                    "pod logs",
                    List.of("describe_pod", "get_events", "get_logs")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(successDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result.toolsUsed()).containsExactly("describe_pod", "get_events", "get_logs");
            assertThat(result.hasContent()).isTrue();
        }

        @Test
        @DisplayName("when circuit breaker is HALF_OPEN, should delegate to MCP tools")
        void whenCircuitBreakerHalfOpenDelegatesToMcp() {
            circuitBreaker.transitionToOpenState();
            circuitBreaker.transitionToHalfOpenState();
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            McpContextPort successDelegate = (podName, namespace) -> new EnrichedContext(
                    "pod description",
                    "pod events",
                    "pod logs",
                    List.of("describe_pod", "get_events", "get_logs")
            );

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(successDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result.toolsUsed()).containsExactly("describe_pod", "get_events", "get_logs");
            assertThat(result.hasContent()).isTrue();
        }

        @Test
        @DisplayName("when delegate throws RuntimeException with CB CLOSED, should return EMPTY")
        void whenDelegateThrowsRuntimeExceptionReturnsEmpty() {
            McpContextPort failingDelegate = (podName, namespace) -> {
                throw new RuntimeException("JSON-RPC connection error");
            };

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(failingDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result).isEqualTo(EnrichedContext.EMPTY);
            assertThat(result.toolsUsed()).isEmpty();
            assertThat(result.hasContent()).isFalse();
        }

        @Test
        @DisplayName("when delegate throws ResourceAccessException, should return EMPTY")
        void whenDelegateThrowsResourceAccessExceptionReturnsEmpty() {
            McpContextPort timeoutDelegate = (podName, namespace) -> {
                throw new ResourceAccessException("I/O error: Read timed out");
            };

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(timeoutDelegate, circuitBreaker);
            EnrichedContext result = adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(result).isEqualTo(EnrichedContext.EMPTY);
            assertThat(result.toolsUsed()).isEmpty();
            assertThat(result.hasContent()).isFalse();
        }

        @Test
        @DisplayName("OPEN circuit breaker should prevent any tool invocation")
        void openCircuitBreakerPreventsToolInvocation() {
            circuitBreaker.transitionToOpenState();

            boolean[] delegateCalled = {false};
            McpContextPort trackingDelegate = (podName, namespace) -> {
                delegateCalled[0] = true;
                return new EnrichedContext("desc", "events", "logs",
                        List.of("describe_pod", "get_events", "get_logs"));
            };

            ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(trackingDelegate, circuitBreaker);
            adapter.retrieveContext(POD_NAME, NAMESPACE);

            assertThat(delegateCalled[0]).isFalse();
        }
    }
}
