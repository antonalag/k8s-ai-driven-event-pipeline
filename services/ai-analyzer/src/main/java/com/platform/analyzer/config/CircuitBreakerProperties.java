package com.platform.analyzer.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration for the Resilience4j Circuit Breaker.
 * Bound to properties prefixed with "platform.resilience.circuit-breaker".
 */
@Validated
@ConfigurationProperties(prefix = "platform.resilience.circuit-breaker")
public record CircuitBreakerProperties(

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
    public CircuitBreakerProperties {
        if (slidingWindowSize == 0) slidingWindowSize = 10;
        if (failureRateThreshold == 0) failureRateThreshold = 50;
        if (waitDurationInOpenState == 0) waitDurationInOpenState = 30;
        if (permittedCallsInHalfOpen == 0) permittedCallsInHalfOpen = 3;
    }
}
