package com.platform.analyzer.config;

import com.platform.analyzer.domain.ports.McpContextPort;
import com.platform.analyzer.infrastructure.client.mcp.McpClientAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

/**
 * Creates the MCP-dedicated Resilience4j CircuitBreaker and the decorated McpContextPort bean.
 * The decorated bean is marked @Primary so OllamaAnalyzerService receives it automatically.
 * Independent from the AI provider circuit breaker ({@link ResilienceConfig}).
 */
@Configuration
@EnableConfigurationProperties(McpCircuitBreakerProperties.class)
public class McpResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(McpResilienceConfig.class);

    @Bean
    CircuitBreaker mcpCircuitBreaker(McpCircuitBreakerProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(
                        Duration.ofSeconds(properties.waitDurationInOpenState()))
                .permittedNumberOfCallsInHalfOpenState(
                        properties.permittedCallsInHalfOpen())
                .recordExceptions(ResourceAccessException.class, RuntimeException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker circuitBreaker = registry.circuitBreaker("mcpCircuitBreaker");

        registerStateTransitionListener(circuitBreaker);

        return circuitBreaker;
    }

    @Bean
    McpClientAdapter mcpClientAdapter(McpProperties mcpProperties) {
        return new McpClientAdapter(mcpProperties);
    }

    @Bean
    @Primary
    McpContextPort resilientMcpContextPort(McpClientAdapter mcpClientAdapter,
                                           CircuitBreaker mcpCircuitBreaker) {
        return new ResilientMcpContextAdapter(mcpClientAdapter, mcpCircuitBreaker);
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
            case CLOSED_TO_OPEN -> log.error(
                    "Circuit breaker state transition. name={}, fromState={}, toState={}, failureRate={}%",
                    cbName, fromState, toState,
                    circuitBreaker.getMetrics().getFailureRate());
            case HALF_OPEN_TO_CLOSED -> log.info(
                    "Circuit breaker state transition. name={}, fromState={}, toState={}, recovered=true",
                    cbName, fromState, toState);
            case OPEN_TO_HALF_OPEN -> log.warn(
                    "Circuit breaker state transition. name={}, fromState={}, toState={}",
                    cbName, fromState, toState);
            case HALF_OPEN_TO_OPEN -> log.warn(
                    "Circuit breaker state transition. name={}, fromState={}, toState={}, recoveryFailed=true",
                    cbName, fromState, toState);
            default -> log.debug(
                    "Circuit breaker state transition. name={}, fromState={}, toState={}",
                    cbName, fromState, toState);
        }
    }
}
