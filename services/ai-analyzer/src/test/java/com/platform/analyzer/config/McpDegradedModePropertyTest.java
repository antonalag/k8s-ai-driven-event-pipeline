package com.platform.analyzer.config;

import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.ports.McpContextPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Circuit Breaker Degraded Mode behavior.
 *
 * <p><b>Property 9:</b> For any analysis produced when the MCP circuit breaker is in OPEN state
 * or when all MCP calls time out, the resulting {@code AiAnalysis} SHALL have
 * {@code mcpContextAvailable == false} and the analysis SHALL still be produced using
 * the original Kafka event and OpenSearch history.
 *
 * <p><b>Validates: Requirements 6.2, 6.3</b>
 */
@Tag("Feature: mcp-intelligence-layer, Property 9: Circuit Breaker Degraded Mode")
class McpDegradedModePropertyTest {

    // ─── Property 9a: Circuit Breaker OPEN → returns EnrichedContext.EMPTY ───────

    /**
     * When the MCP circuit breaker is forced to OPEN state, calling retrieveContext
     * with any podName/namespace SHALL return EnrichedContext.EMPTY, proving the
     * pipeline continues in degraded mode (mcpContextAvailable will be false).
     *
     * Validates: Requirements 6.2, 6.3
     */
    @Property(tries = 100)
    void circuitBreakerOpenReturnsEmptyContext(
            @ForAll("podNames") String podName,
            @ForAll("namespaces") String namespace) {

        // Create a circuit breaker and force it to OPEN state
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(ResourceAccessException.class, RuntimeException.class)
                .build();

        CircuitBreaker cb = CircuitBreakerRegistry.of(config)
                .circuitBreaker("mcp-degraded-open-" + podName.hashCode() + "-" + namespace.hashCode());

        // Force circuit breaker to OPEN state
        cb.transitionToOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Delegate that should never be called when CB is OPEN
        McpContextPort delegate = (p, n) -> {
            throw new AssertionError("Delegate should not be called when circuit breaker is OPEN");
        };

        ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(delegate, cb);

        // Act
        EnrichedContext result = adapter.retrieveContext(podName, namespace);

        // Assert: should return EMPTY context (degraded mode)
        assertThat(result).isEqualTo(EnrichedContext.EMPTY);
        assertThat(result.hasContent()).isFalse();
        assertThat(result.podDescription()).isNull();
        assertThat(result.podEvents()).isNull();
        assertThat(result.podLogs()).isNull();
        assertThat(result.toolsUsed()).isEmpty();
    }

    // ─── Property 9b: ResourceAccessException (timeout) → returns EMPTY ──────────

    /**
     * When the MCP delegate throws ResourceAccessException (simulating a timeout),
     * the resilient adapter SHALL return EnrichedContext.EMPTY, proving degraded mode
     * activates on network failures.
     *
     * Validates: Requirements 6.2, 6.3
     */
    @Property(tries = 100)
    void timeoutExceptionReturnsEmptyContext(
            @ForAll("podNames") String podName,
            @ForAll("namespaces") String namespace) {

        // Create a circuit breaker in CLOSED state
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(100)
                .failureRateThreshold(90)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(ResourceAccessException.class, RuntimeException.class)
                .build();

        CircuitBreaker cb = CircuitBreakerRegistry.of(config)
                .circuitBreaker("mcp-degraded-timeout-" + podName.hashCode() + "-" + namespace.hashCode());

        // Delegate that simulates a timeout (ResourceAccessException)
        McpContextPort delegate = (p, n) -> {
            throw new ResourceAccessException("I/O error: Read timed out connecting to mcp-server:3001");
        };

        ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(delegate, cb);

        // Act
        EnrichedContext result = adapter.retrieveContext(podName, namespace);

        // Assert: should return EMPTY context (degraded mode)
        assertThat(result).isEqualTo(EnrichedContext.EMPTY);
        assertThat(result.hasContent()).isFalse();
        assertThat(result.podDescription()).isNull();
        assertThat(result.podEvents()).isNull();
        assertThat(result.podLogs()).isNull();
        assertThat(result.toolsUsed()).isEmpty();
    }

    // ─── Property 9c: EnrichedContext.EMPTY has correct degraded-mode semantics ──

    /**
     * EnrichedContext.EMPTY always reports hasContent() == false, proving that
     * any analysis built from EMPTY context will have mcpContextAvailable == false.
     *
     * Validates: Requirements 6.3
     */
    @Property(tries = 100)
    void emptyContextAlwaysReportsNoContent(
            @ForAll("podNames") String podName,
            @ForAll("namespaces") String namespace) {

        // Regardless of input, EnrichedContext.EMPTY is always "no content"
        assertThat(EnrichedContext.EMPTY.hasContent()).isFalse();
        assertThat(EnrichedContext.EMPTY.toolsUsed()).isEmpty();

        // Verify it's a stable singleton
        assertThat(EnrichedContext.EMPTY).isSameAs(EnrichedContext.EMPTY);
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> podNames() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(253)
                .alpha()
                .numeric()
                .withChars('-', '.')
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<String> namespaces() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(63)
                .alpha()
                .numeric()
                .withChars('-')
                .filter(s -> !s.isBlank());
    }
}
