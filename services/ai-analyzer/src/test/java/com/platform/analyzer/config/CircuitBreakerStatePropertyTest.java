package com.platform.analyzer.config;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for circuit breaker state transition behavior.
 * Feature: pipeline-resilience
 */
@Tag("Feature: pipeline-resilience")
class CircuitBreakerStatePropertyTest {

    private static final KubernetesEvent SAMPLE_EVENT =
            new KubernetesEvent("test-pod", "default", PodPhase.Failed, Instant.now());

    private static final AiAnalysis SUCCESS_ANALYSIS =
            new AiAnalysis("test-pod", "default", "CRITICAL", "OOMKilled", List.of("Increase memory"));

    // ─── Property 9: Failure rate threshold triggers OPEN transition ─────────────

    /**
     * Property 9: Failure rate threshold triggers OPEN transition
     * Validates: Requirements 1.2
     *
     * For any sliding window of size N where the number of failures exceeds the
     * failure rate threshold, the Circuit Breaker transitions from CLOSED to OPEN.
     */
    @Property(tries = 100)
    void failureRateExceedingThresholdTriggersOpen(
            @ForAll("windowSizes") int windowSize,
            @ForAll("thresholds") int threshold) {

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .failureRateThreshold(threshold)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(AiAnalysisException.class, ResourceAccessException.class)
                .build();

        CircuitBreaker cb = CircuitBreakerRegistry.of(config)
                .circuitBreaker("threshold-test-" + windowSize + "-" + threshold);

        // Calculate minimum failures needed to exceed threshold
        int failuresNeeded = (int) Math.ceil((threshold / 100.0) * windowSize) + 1;
        // Ensure we don't exceed window size
        failuresNeeded = Math.min(failuresNeeded, windowSize);
        int successes = windowSize - failuresNeeded;

        AiLanguageModelPort successDelegate = (e, h) -> SUCCESS_ANALYSIS;
        AiLanguageModelPort failDelegate = (e, h) -> {
            throw new AiAnalysisException("simulated failure");
        };

        // First record successes
        ResilientAiLanguageModelAdapter successAdapter =
                new ResilientAiLanguageModelAdapter(successDelegate, cb);
        for (int i = 0; i < successes; i++) {
            successAdapter.analyze(SAMPLE_EVENT, List.of());
        }

        // Then record failures to exceed threshold
        ResilientAiLanguageModelAdapter failAdapter =
                new ResilientAiLanguageModelAdapter(failDelegate, cb);
        for (int i = 0; i < failuresNeeded; i++) {
            failAdapter.analyze(SAMPLE_EVENT, List.of());
        }

        // CB should have transitioned to OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Integer> windowSizes() {
        return Arbitraries.integers().between(5, 20);
    }

    @Provide
    Arbitrary<Integer> thresholds() {
        return Arbitraries.integers().between(20, 80);
    }
}
