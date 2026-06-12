package com.platform.analyzer.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration for the MCP-dedicated Resilience4j Circuit Breaker.
 * Bound to properties prefixed with "platform.mcp.circuit-breaker".
 * Independent from the AI provider circuit breaker ({@link CircuitBreakerProperties}).
 */
@Validated
@ConfigurationProperties(prefix = "platform.mcp.circuit-breaker")
public record McpCircuitBreakerProperties(

        @Min(2) @Max(1000)
        int slidingWindowSize,

        @Min(1) @Max(100)
        int failureRateThreshold,

        @Min(1) @Max(300)
        int waitDurationInOpenState,

        @Min(1) @Max(100)
        int permittedCallsInHalfOpen

) {
    /**
     * Provides default values when properties are not explicitly defined.
     */
    public McpCircuitBreakerProperties {
        if (slidingWindowSize == 0) slidingWindowSize = 10;
        if (failureRateThreshold == 0) failureRateThreshold = 50;
        if (waitDurationInOpenState == 0) waitDurationInOpenState = 30;
        if (permittedCallsInHalfOpen == 0) permittedCallsInHalfOpen = 3;
    }
}
