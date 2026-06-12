package com.platform.analyzer.config;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for the ResilientAiLanguageModelAdapter decorator.
 * Feature: pipeline-resilience
 */
@Tag("Feature: pipeline-resilience")
class ResilientAdapterPropertyTest {

    // ─── Generators ──────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<KubernetesEvent> kubernetesEvents() {
        Arbitrary<String> podNames = Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(63);
        Arbitrary<String> namespaces = Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(63);
        Arbitrary<PodPhase> phases = Arbitraries.of(PodPhase.class);
        Arbitrary<Instant> timestamps = Arbitraries.longs()
                .between(0, 2_000_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(podNames, namespaces, phases, timestamps)
                .as(KubernetesEvent::new);
    }

    @Provide
    Arbitrary<AiAnalysis> aiAnalyses() {
        Arbitrary<String> podNames = Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(63);
        Arbitrary<String> namespaces = Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(63);
        Arbitrary<String> verdicts = Arbitraries.of("CRITICAL", "WARNING", "HEALTHY", "UNKNOWN");
        Arbitrary<String> rootCauses = Arbitraries.strings()
                .alpha().ofMinLength(5).ofMaxLength(200);
        Arbitrary<List<String>> actions = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(50)
                .list().ofMinSize(1).ofMaxSize(5);

        return Combinators.combine(podNames, namespaces, verdicts, rootCauses, actions)
                .as(AiAnalysis::new);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private CircuitBreaker createClosedCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(AiAnalysisException.class, ResourceAccessException.class)
                .build();
        return CircuitBreakerRegistry.of(config).circuitBreaker("test-closed");
    }

    private CircuitBreaker createOpenCircuitBreaker() {
        CircuitBreaker cb = createClosedCircuitBreaker();
        cb.transitionToOpenState();
        return cb;
    }

    // ─── Property 1: Fallback structural correctness ─────────────────────────────

    /**
     * Property 1: Fallback structural correctness
     * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6
     *
     * For any valid KubernetesEvent, buildDegradedAnalysis() produces an AiAnalysis
     * where all fields satisfy the degraded contract invariants.
     */
    @Property(tries = 200)
    void fallbackProducesStructurallyCorrectAnalysis(
            @ForAll("kubernetesEvents") KubernetesEvent event) {

        CircuitBreaker cb = createClosedCircuitBreaker();
        ResilientAiLanguageModelAdapter adapter =
                new ResilientAiLanguageModelAdapter((e, h, c) -> null, cb);

        AiAnalysis result = adapter.buildDegradedAnalysis(event);

        assertThat(result).isNotNull();
        assertThat(result.podName()).isEqualTo(event.podName());
        assertThat(result.namespace()).isEqualTo(event.namespace());
        assertThat(result.verdict()).isEqualTo("DEGRADED");
        assertThat(result.rootCauseAnalysis())
                .isEqualTo("AI provider unavailable (circuit breaker open)");
        assertThat(result.recommendedActions()).hasSize(1);
        assertThat(result.recommendedActions().get(0))
                .isEqualTo("Retry after provider recovery");
        assertThat(result.mcpToolsUsed()).isEmpty();
        assertThat(result.mcpContextAvailable()).isFalse();
        // All fields non-null
        assertThat(result.podName()).isNotNull();
        assertThat(result.namespace()).isNotNull();
        assertThat(result.verdict()).isNotNull();
        assertThat(result.rootCauseAnalysis()).isNotNull();
        assertThat(result.recommendedActions()).isNotNull();
    }

    // ─── Property 2: Exception classification — failures are recorded ────────────

    /**
     * Property 2: Exception classification — failures are recorded
     * Validates: Requirements 1.8, 3.1, 3.2, 4.2
     *
     * For any invocation throwing AiAnalysisException or ResourceAccessException,
     * the decorator returns a degraded AiAnalysis instead of propagating.
     */
    @Property(tries = 200)
    void aiAnalysisExceptionReturnsfallback(
            @ForAll("kubernetesEvents") KubernetesEvent event,
            @ForAll("exceptionMessages") String message) {

        CircuitBreaker cb = createClosedCircuitBreaker();
        AiLanguageModelPort failingDelegate = (e, h, c) -> {
            throw new AiAnalysisException(message);
        };
        ResilientAiLanguageModelAdapter adapter =
                new ResilientAiLanguageModelAdapter(failingDelegate, cb);

        AiAnalysis result = adapter.analyze(event, List.of());

        assertThat(result.verdict()).isEqualTo("DEGRADED");
        assertThat(result.podName()).isEqualTo(event.podName());
        assertThat(result.namespace()).isEqualTo(event.namespace());
    }

    @Property(tries = 200)
    void resourceAccessExceptionReturnsfallback(
            @ForAll("kubernetesEvents") KubernetesEvent event,
            @ForAll("exceptionMessages") String message) {

        CircuitBreaker cb = createClosedCircuitBreaker();
        AiLanguageModelPort failingDelegate = (e, h, c) -> {
            throw new ResourceAccessException(message);
        };
        ResilientAiLanguageModelAdapter adapter =
                new ResilientAiLanguageModelAdapter(failingDelegate, cb);

        AiAnalysis result = adapter.analyze(event, List.of());

        assertThat(result.verdict()).isEqualTo("DEGRADED");
        assertThat(result.podName()).isEqualTo(event.podName());
        assertThat(result.namespace()).isEqualTo(event.namespace());
    }

    // ─── Property 3: Non-matching exceptions propagate unchanged ─────────────────

    /**
     * Property 3: Non-matching exceptions propagate unchanged
     * Validates: Requirements 3.3
     *
     * For any exception that is NOT AiAnalysisException nor ResourceAccessException,
     * the decorator propagates it unchanged.
     */
    @Property(tries = 200)
    void nonMatchingExceptionsPropagateUnchanged(
            @ForAll("kubernetesEvents") KubernetesEvent event,
            @ForAll("exceptionMessages") String message) {

        CircuitBreaker cb = createClosedCircuitBreaker();
        IllegalStateException originalEx = new IllegalStateException(message);
        AiLanguageModelPort failingDelegate = (e, h, c) -> {
            throw originalEx;
        };
        ResilientAiLanguageModelAdapter adapter =
                new ResilientAiLanguageModelAdapter(failingDelegate, cb);

        assertThatThrownBy(() -> adapter.analyze(event, List.of()))
                .isSameAs(originalEx);
    }

    // ─── Property 6: OPEN state bypasses delegate ────────────────────────────────

    /**
     * Property 6: OPEN state bypasses delegate
     * Validates: Requirements 1.3
     *
     * While CB is OPEN, the delegate is never invoked and a degraded analysis is returned.
     */
    @Property(tries = 200)
    void openStateBypasesDelegate(
            @ForAll("kubernetesEvents") KubernetesEvent event) {

        CircuitBreaker cb = createOpenCircuitBreaker();
        boolean[] delegateCalled = {false};
        AiLanguageModelPort delegate = (e, h, c) -> {
            delegateCalled[0] = true;
            return new AiAnalysis("x", "y", "CRITICAL", "test", List.of("action"));
        };
        ResilientAiLanguageModelAdapter adapter =
                new ResilientAiLanguageModelAdapter(delegate, cb);

        AiAnalysis result = adapter.analyze(event, List.of());

        assertThat(delegateCalled[0]).isFalse();
        assertThat(result.verdict()).isEqualTo("DEGRADED");
        assertThat(result.podName()).isEqualTo(event.podName());
    }

    // ─── Property 7: CallNotPermittedException translated ────────────────────────

    /**
     * Property 7: Resilience4j exceptions are translated to domain exceptions
     * Validates: Requirements 6.5
     *
     * CallNotPermittedException never escapes the decorator — always returns fallback.
     */
    @Property(tries = 200)
    void callNotPermittedExceptionNeverEscapes(
            @ForAll("kubernetesEvents") KubernetesEvent event) {

        CircuitBreaker cb = createOpenCircuitBreaker();
        AiLanguageModelPort delegate = (e, h, c) -> {
            throw new RuntimeException("should not be called");
        };
        ResilientAiLanguageModelAdapter adapter =
                new ResilientAiLanguageModelAdapter(delegate, cb);

        // Should NOT throw CallNotPermittedException
        AiAnalysis result = adapter.analyze(event, List.of());

        assertThat(result).isNotNull();
        assertThat(result.verdict()).isEqualTo("DEGRADED");
    }

    // ─── Property 8: CLOSED state passthrough with success recording ─────────────

    /**
     * Property 8: CLOSED state passthrough with success recording
     * Validates: Requirements 1.1, 3.4
     *
     * In CLOSED state, the delegate's result is returned unchanged.
     */
    @Property(tries = 200)
    void closedStatePassthroughReturnsDelegateResult(
            @ForAll("kubernetesEvents") KubernetesEvent event,
            @ForAll("aiAnalyses") AiAnalysis expectedAnalysis) {

        CircuitBreaker cb = createClosedCircuitBreaker();
        AiLanguageModelPort delegate = (e, h, c) -> expectedAnalysis;
        ResilientAiLanguageModelAdapter adapter =
                new ResilientAiLanguageModelAdapter(delegate, cb);

        AiAnalysis result = adapter.analyze(event, List.of());

        assertThat(result).isSameAs(expectedAnalysis);
    }

    // ─── Arbitrary Providers ─────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> exceptionMessages() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100);
    }
}
