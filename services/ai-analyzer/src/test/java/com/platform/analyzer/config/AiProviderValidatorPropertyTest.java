package com.platform.analyzer.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for AiProviderValidator fail-fast behavior.
 * Feature: dynamic-ai-provider-routing, Property 2: Fail-fast rejection of invalid provider values.
 */
@Tag("Feature: dynamic-ai-provider-routing, Property 2: Fail-fast rejection of invalid provider values")
class AiProviderValidatorPropertyTest {

    private static final Set<String> VALID_PROVIDERS = Set.of("ollama", "byok");

    @Property(tries = 200)
    void invalidProviderValueShouldThrowIllegalStateException(
            @ForAll @StringLength(min = 1, max = 50) String invalidValue) {

        Assume.that(!VALID_PROVIDERS.contains(invalidValue));

        var properties = new PlatformProperties(
                new PlatformProperties.Ai(invalidValue),
                new PlatformProperties.Storage("opensearch"),
                new PlatformProperties.Messaging("kafka")
        );
        var validator = new AiProviderValidator(properties);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(invalidValue)
                .hasMessageContaining("ollama")
                .hasMessageContaining("byok");
    }

    @Property(tries = 100)
    void blankProviderValueShouldThrowIllegalStateException(
            @ForAll("blankStrings") String blankValue) {

        var properties = new PlatformProperties(
                new PlatformProperties.Ai(blankValue),
                new PlatformProperties.Storage("opensearch"),
                new PlatformProperties.Messaging("kafka")
        );
        var validator = new AiProviderValidator(properties);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("platform.ai.provider");
    }

    @Property(tries = 50)
    void nullAiSectionShouldThrowIllegalStateException() {
        var properties = new PlatformProperties(
                null,
                new PlatformProperties.Storage("opensearch"),
                new PlatformProperties.Messaging("kafka")
        );
        var validator = new AiProviderValidator(properties);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("platform.ai.provider");
    }

    @Example
    void validOllamaShouldNotThrow() {
        var properties = new PlatformProperties(
                new PlatformProperties.Ai("ollama"),
                new PlatformProperties.Storage("opensearch"),
                new PlatformProperties.Messaging("kafka")
        );
        var validator = new AiProviderValidator(properties);

        validator.validate(); // should not throw
    }

    @Example
    void validByokShouldNotThrow() {
        var properties = new PlatformProperties(
                new PlatformProperties.Ai("byok"),
                new PlatformProperties.Storage("opensearch"),
                new PlatformProperties.Messaging("kafka")
        );
        var validator = new AiProviderValidator(properties);

        validator.validate(); // should not throw
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", "\n", " \t\n ");
    }
}
