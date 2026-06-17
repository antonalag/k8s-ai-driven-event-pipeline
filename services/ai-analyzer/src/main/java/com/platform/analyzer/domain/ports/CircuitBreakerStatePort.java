package com.platform.analyzer.domain.ports;

/**
 * Port for querying the MCP circuit breaker state.
 * Decouples the service layer from Resilience4j infrastructure.
 */
public interface CircuitBreakerStatePort {

    /**
     * Returns the current MCP circuit breaker state as a string.
     *
     * @return one of "CLOSED", "OPEN", "HALF_OPEN", or "UNKNOWN" on error
     */
    String getMcpCircuitBreakerState();
}
