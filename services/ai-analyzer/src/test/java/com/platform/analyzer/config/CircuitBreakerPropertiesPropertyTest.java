package com.platform.analyzer.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for CircuitBreakerProperties record validation.
 * Feature: pipeline-resilience
 */
@Tag("Feature: pipeline-resilience")
class CircuitBreakerPropertiesPropertyTest {

    private static final Validator validator;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ─── Property 4: Configuration binding within valid ranges ────────────────────

    /**
     * Property 4: Configuration binding within valid ranges
     * Validates: Requirements 2.1, 2.2, 2.3, 2.4
     *
     * For any set of integer values within valid ranges, the record constructs
     * successfully and validates without violations.
     */
    @Property(tries = 200)
    void validRangeValuesBindSuccessfully(
            @ForAll("validSlidingWindowSize") int slidingWindowSize,
            @ForAll("validFailureRateThreshold") int failureRateThreshold,
            @ForAll("validWaitDuration") int waitDurationInOpenState,
            @ForAll("validPermittedCalls") int permittedCallsInHalfOpen) {

        CircuitBreakerProperties props = new CircuitBreakerProperties(
                slidingWindowSize,
                failureRateThreshold,
                waitDurationInOpenState,
                permittedCallsInHalfOpen
        );

        Set<ConstraintViolation<CircuitBreakerProperties>> violations = validator.validate(props);

        assertThat(violations).isEmpty();
        assertThat(props.slidingWindowSize()).isEqualTo(slidingWindowSize);
        assertThat(props.failureRateThreshold()).isEqualTo(failureRateThreshold);
        assertThat(props.waitDurationInOpenState()).isEqualTo(waitDurationInOpenState);
        assertThat(props.permittedCallsInHalfOpen()).isEqualTo(permittedCallsInHalfOpen);
    }

    // ─── Property 5: Invalid configuration rejected ──────────────────────────────

    /**
     * Property 5: Invalid configuration prevents startup
     * Validates: Requirements 2.6
     *
     * For any value outside its valid range, Bean Validation rejects the configuration.
     */
    @Property(tries = 200)
    void invalidSlidingWindowSizeIsRejected(
            @ForAll("invalidSlidingWindowSize") int slidingWindowSize) {

        CircuitBreakerProperties props = new CircuitBreakerProperties(
                slidingWindowSize, 50, 30, 3
        );

        Set<ConstraintViolation<CircuitBreakerProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
    }

    @Property(tries = 200)
    void invalidFailureRateThresholdIsRejected(
            @ForAll("invalidFailureRateThreshold") int failureRateThreshold) {

        CircuitBreakerProperties props = new CircuitBreakerProperties(
                10, failureRateThreshold, 30, 3
        );

        Set<ConstraintViolation<CircuitBreakerProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
    }

    @Property(tries = 200)
    void invalidWaitDurationIsRejected(
            @ForAll("invalidWaitDuration") int waitDuration) {

        CircuitBreakerProperties props = new CircuitBreakerProperties(
                10, 50, waitDuration, 3
        );

        Set<ConstraintViolation<CircuitBreakerProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
    }

    @Property(tries = 200)
    void invalidPermittedCallsIsRejected(
            @ForAll("invalidPermittedCalls") int permittedCalls) {

        CircuitBreakerProperties props = new CircuitBreakerProperties(
                10, 50, 30, permittedCalls
        );

        Set<ConstraintViolation<CircuitBreakerProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Integer> validSlidingWindowSize() {
        return Arbitraries.integers().between(2, 1000);
    }

    @Provide
    Arbitrary<Integer> validFailureRateThreshold() {
        return Arbitraries.integers().between(1, 100);
    }

    @Provide
    Arbitrary<Integer> validWaitDuration() {
        return Arbitraries.integers().between(1, 300);
    }

    @Provide
    Arbitrary<Integer> validPermittedCalls() {
        return Arbitraries.integers().between(1, 100);
    }

    @Provide
    Arbitrary<Integer> invalidSlidingWindowSize() {
        // Exclude 0 because the compact constructor maps 0 → default (10)
        return Arbitraries.oneOf(
                Arbitraries.integers().between(Integer.MIN_VALUE, -1),
                Arbitraries.just(1),
                Arbitraries.integers().between(1001, Integer.MAX_VALUE)
        );
    }

    @Provide
    Arbitrary<Integer> invalidFailureRateThreshold() {
        // Exclude 0 because the compact constructor maps 0 → default (50)
        return Arbitraries.oneOf(
                Arbitraries.integers().between(Integer.MIN_VALUE, -1),
                Arbitraries.integers().between(101, Integer.MAX_VALUE)
        );
    }

    @Provide
    Arbitrary<Integer> invalidWaitDuration() {
        // Exclude 0 because the compact constructor maps 0 → default (30)
        return Arbitraries.oneOf(
                Arbitraries.integers().between(Integer.MIN_VALUE, -1),
                Arbitraries.integers().between(301, Integer.MAX_VALUE)
        );
    }

    @Provide
    Arbitrary<Integer> invalidPermittedCalls() {
        // Exclude 0 because the compact constructor maps 0 → default (3)
        return Arbitraries.oneOf(
                Arbitraries.integers().between(Integer.MIN_VALUE, -1),
                Arbitraries.integers().between(101, Integer.MAX_VALUE)
        );
    }
}
