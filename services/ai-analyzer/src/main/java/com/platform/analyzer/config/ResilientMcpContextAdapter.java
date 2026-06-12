package com.platform.analyzer.config;

import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.ports.McpContextPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

/**
 * Decorator that wraps the {@link com.platform.analyzer.infrastructure.client.mcp.McpClientAdapter}
 * with a dedicated MCP circuit breaker. Produces {@link EnrichedContext#EMPTY} on failure
 * or when the circuit is OPEN.
 *
 * <p>Records connection timeouts, read timeouts, and JSON-RPC errors as failures
 * in the circuit breaker.
 *
 * <p>Resides in the config layer to preserve absolute domain purity.
 */
public class ResilientMcpContextAdapter implements McpContextPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientMcpContextAdapter.class);

    private final McpContextPort delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientMcpContextAdapter(McpContextPort delegate, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public EnrichedContext retrieveContext(String podName, String namespace) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.retrieveContext(podName, namespace));
        } catch (CallNotPermittedException ex) {
            log.warn("MCP circuit breaker OPEN — returning empty context. podName={}, namespace={}, cbState={}",
                    podName, namespace, circuitBreaker.getState());
            return EnrichedContext.EMPTY;
        } catch (ResourceAccessException ex) {
            log.warn("MCP network failure — returning empty context. podName={}, namespace={}, cause={}",
                    podName, namespace, ex.getMessage());
            return EnrichedContext.EMPTY;
        } catch (RuntimeException ex) {
            log.warn("MCP invocation failure — returning empty context. podName={}, namespace={}, cause={}",
                    podName, namespace, ex.getMessage());
            return EnrichedContext.EMPTY;
        }
    }
}
