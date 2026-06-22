package com.platform.analyzer.infrastructure.resilience;

import com.platform.analyzer.domain.ports.CircuitBreakerStatePort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Adapter that resolves the MCP circuit breaker state from the Resilience4j registry.
 * Resides in the infrastructure layer to keep the service layer framework-free.
 */
@Component
public class CircuitBreakerStateAdapter implements CircuitBreakerStatePort {

    private static final String MCP_CB_NAME = "mcpCircuitBreaker";

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerStateAdapter(
            @Qualifier("mcpCircuitBreakerRegistry") CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public String getMcpCircuitBreakerState() {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(MCP_CB_NAME);
            return cb.getState().name();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
