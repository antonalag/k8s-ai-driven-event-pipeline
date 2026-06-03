package com.platform.analyzer.config;

import com.platform.analyzer.domain.ports.AiAnalysisException;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

/**
 * Creates the Resilience4j CircuitBreaker and the decorated AiLanguageModelPort bean.
 * The decorated bean is marked @Primary so OllamaAnalyzerService receives it automatically.
 */
@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    @Bean
    CircuitBreaker aiCircuitBreaker(CircuitBreakerProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(
                        Duration.ofSeconds(properties.waitDurationInOpenState()))
                .permittedNumberOfCallsInHalfOpenState(
                        properties.permittedCallsInHalfOpen())
                .recordExceptions(AiAnalysisException.class, ResourceAccessException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker circuitBreaker = registry.circuitBreaker("aiLanguageModel");

        registerStateTransitionListener(circuitBreaker);

        return circuitBreaker;
    }

    @Bean
    @Primary
    AiLanguageModelPort resilientAiLanguageModelPort(
            @Qualifier("aiLanguageModelPort") AiLanguageModelPort delegate,
            CircuitBreaker aiCircuitBreaker) {
        return new ResilientAiLanguageModelAdapter(delegate, aiCircuitBreaker);
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

        switch (transition) {
            case CLOSED_TO_OPEN -> log.error(
                    "Circuit breaker state transition. fromState={}, toState={}, failureRate={}%",
                    fromState, toState,
                    circuitBreaker.getMetrics().getFailureRate());
            case OPEN_TO_HALF_OPEN -> log.info(
                    "Circuit breaker state transition. fromState={}, toState={}",
                    fromState, toState);
            case HALF_OPEN_TO_CLOSED -> log.info(
                    "Circuit breaker state transition. fromState={}, toState={}, recovered=true",
                    fromState, toState);
            case HALF_OPEN_TO_OPEN -> log.warn(
                    "Circuit breaker state transition. fromState={}, toState={}, recoveryFailed=true",
                    fromState, toState);
            default -> log.debug(
                    "Circuit breaker state transition. fromState={}, toState={}",
                    fromState, toState);
        }
    }
}
