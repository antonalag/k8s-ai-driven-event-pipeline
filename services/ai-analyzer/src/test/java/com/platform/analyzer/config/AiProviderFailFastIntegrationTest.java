package com.platform.analyzer.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying fail-fast startup when platform.ai.provider is invalid.
 * Feature: dynamic-ai-provider-routing.
 * Validates: Requirements 1.5, 4.1, 4.2.
 */
@Tag("Feature: dynamic-ai-provider-routing")
class AiProviderFailFastIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ValidatorTestConfig.class);

    @EnableConfigurationProperties(PlatformProperties.class)
    @Import(AiProviderValidator.class)
    static class ValidatorTestConfig {
    }

    @Test
    void shouldFailWithInvalidProviderValue() {
        contextRunner
                .withPropertyValues(
                        "platform.ai.provider=invalid",
                        "platform.storage.type=opensearch",
                        "platform.messaging.type=kafka"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("invalid")
                            .hasMessageContaining("platform.ai.provider");
                });
    }

    @Test
    void shouldFailWhenProviderPropertyIsMissing() {
        contextRunner
                .withPropertyValues(
                        "platform.storage.type=opensearch",
                        "platform.messaging.type=kafka"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("platform.ai.provider");
                });
    }

    @Test
    void shouldFailWithEmptyProviderValue() {
        contextRunner
                .withPropertyValues(
                        "platform.ai.provider=",
                        "platform.storage.type=opensearch",
                        "platform.messaging.type=kafka"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("platform.ai.provider");
                });
    }

    @Test
    void shouldSucceedWithValidOllamaProvider() {
        contextRunner
                .withPropertyValues(
                        "platform.ai.provider=ollama",
                        "platform.storage.type=opensearch",
                        "platform.messaging.type=kafka"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldSucceedWithValidByokProvider() {
        contextRunner
                .withPropertyValues(
                        "platform.ai.provider=byok",
                        "platform.storage.type=opensearch",
                        "platform.messaging.type=kafka"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }
}
