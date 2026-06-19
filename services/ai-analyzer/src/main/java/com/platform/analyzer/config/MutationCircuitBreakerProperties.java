package com.platform.analyzer.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration for the Mutation-dedicated Resilience4j Circuit Breaker.
 * Bound to properties prefixed with "platform.resilience.mutation".
 *
 * <p>Isolated from the AI provider circuit breaker ({@link CircuitBreakerProperties})
 * and the MCP read-path circuit breaker ({@link McpCircuitBreakerProperties}).
 * This ensures that write-path failures do not degrade the diagnostic pipeline.
 */
@Validated
@ConfigurationProperties(prefix = "platform.resilience.mutation")
public record MutationCircuitBreakerProperties(

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
     * Shorter window (5) for faster trip on write failures.
     */
    public MutationCircuitBreakerProperties {
        if (slidingWindowSize == 0) slidingWindowSize = 5;
        if (failureRateThreshold == 0) failureRateThreshold = 50;
        if (waitDurationInOpenState == 0) waitDurationInOpenState = 30;
        if (permittedCallsInHalfOpen == 0) permittedCallsInHalfOpen = 2;
    }
}
