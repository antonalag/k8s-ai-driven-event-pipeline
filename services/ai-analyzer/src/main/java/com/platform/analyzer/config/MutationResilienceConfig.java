package com.platform.analyzer.config;

import com.platform.analyzer.domain.ports.RemediationException;
import com.platform.analyzer.domain.ports.RemediationPort;
import com.platform.analyzer.infrastructure.client.mcp.RemediationAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

/**
 * Creates the Mutation-dedicated Resilience4j CircuitBreaker and the RemediationPort bean.
 *
 * <p>The {@code mutationCircuitBreaker} is completely isolated from the read-path
 * {@code mcpCircuitBreaker} and the AI provider circuit breaker.
 * Write-path failures (connection issues, timeouts) do not contaminate diagnostic reads.
 *
 * <p>Only {@link RemediationException} and {@link ResourceAccessException} are recorded as failures.
 * MCP tool-level errors (RESOURCE_NOT_FOUND, INVALID_PARAMS) are NOT recorded as failures
 * because they represent client-side errors, not infrastructure instability.
 */
@Configuration
@EnableConfigurationProperties(MutationCircuitBreakerProperties.class)
public class MutationResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(MutationResilienceConfig.class);

    @Bean
    CircuitBreakerRegistry mutationCircuitBreakerRegistry(MutationCircuitBreakerProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(
                        Duration.ofSeconds(properties.waitDurationInOpenState()))
                .permittedNumberOfCallsInHalfOpenState(
                        properties.permittedCallsInHalfOpen())
                .minimumNumberOfCalls(3)
                .recordExceptions(RemediationException.class, ResourceAccessException.class)
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    CircuitBreaker mutationCircuitBreaker(CircuitBreakerRegistry mutationCircuitBreakerRegistry) {
        CircuitBreaker circuitBreaker = mutationCircuitBreakerRegistry.circuitBreaker("mutationCircuitBreaker");
        registerStateTransitionListener(circuitBreaker);
        return circuitBreaker;
    }

    @Bean
    RemediationAdapter remediationAdapter(McpProperties mcpProperties) {
        return new RemediationAdapter(mcpProperties);
    }

    @Bean
    RemediationPort remediationPort(RemediationAdapter remediationAdapter,
                                    CircuitBreaker mutationCircuitBreaker) {
        return new ResilientRemediationPortAdapter(remediationAdapter, mutationCircuitBreaker);
    }

    private void registerStateTransitionListener(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> logStateTransition(event, circuitBreaker));
    }

    private void logStateTransition(CircuitBreakerOnStateTransitionEvent event,
                                    CircuitBreaker circuitBreaker) {
        var transition = event.getStateTransition();
        var fromState = transition.getFromState().name();
        var toState = transition.getToState().name();
        var cbName = circuitBreaker.getName();

        switch (transition) {
            case CLOSED_TO_OPEN -> log.warn(
                    "Mutation circuit breaker state transition. name={}, fromState={}, toState={}, failureRate={}%",
                    cbName, fromState, toState,
                    circuitBreaker.getMetrics().getFailureRate());
            case HALF_OPEN_TO_CLOSED -> log.info(
                    "Mutation circuit breaker state transition. name={}, fromState={}, toState={}, recovered=true",
                    cbName, fromState, toState);
            case OPEN_TO_HALF_OPEN -> log.warn(
                    "Mutation circuit breaker state transition. name={}, fromState={}, toState={}",
                    cbName, fromState, toState);
            case HALF_OPEN_TO_OPEN -> log.warn(
                    "Mutation circuit breaker state transition. name={}, fromState={}, toState={}, recoveryFailed=true",
                    cbName, fromState, toState);
            default -> log.debug(
                    "Mutation circuit breaker state transition. name={}, fromState={}, toState={}",
                    cbName, fromState, toState);
        }
    }
}
