package com.platform.analyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the ResilienceConfig and decorator wiring.
 * Feature: pipeline-resilience
 * Validates: Requirements 6.4, 8.1, 8.2
 */
@Tag("Feature: pipeline-resilience")
class ResilienceIntegrationTest {

    private static final AiAnalysis DELEGATE_RESULT =
            new AiAnalysis("my-pod", "production", "CRITICAL", "OOMKilled", List.of("Increase memory"));

    @Configuration
    @EnableConfigurationProperties(PlatformProperties.class)
    static class TestDelegateConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AiLanguageModelPort aiLanguageModelPort() {
            return (event, history, context) -> DELEGATE_RESULT;
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestDelegateConfig.class, ResilienceConfig.class, ValidationAutoConfiguration.class)
            .withPropertyValues(
                    "platform.ai.provider=ollama",
                    "platform.storage.type=opensearch",
                    "platform.messaging.type=kafka",
                    "platform.resilience.circuit-breaker.sliding-window-size=10",
                    "platform.resilience.circuit-breaker.failure-rate-threshold=50",
                    "platform.resilience.circuit-breaker.wait-duration-in-open-state=30",
                    "platform.resilience.circuit-breaker.permitted-calls-in-half-open=3"
            );

    @Test
    void primaryBeanShouldBeResilientAdapter() {
        contextRunner.run(context -> {
            // There are two AiLanguageModelPort beans: the delegate and the @Primary decorator
            assertThat(context).hasBean("resilientAiLanguageModelPort");
            AiLanguageModelPort bean = context.getBean(AiLanguageModelPort.class);
            assertThat(bean).isInstanceOf(ResilientAiLanguageModelAdapter.class);
        });
    }

    @Test
    void delegateShouldReceiveCallsInClosedState() {
        contextRunner.run(context -> {
            AiLanguageModelPort port = context.getBean(AiLanguageModelPort.class);
            KubernetesEvent event = new KubernetesEvent(
                    "my-pod", "production", PodPhase.Failed, Instant.now());

            AiAnalysis result = port.analyze(event, List.of());

            assertThat(result).isSameAs(DELEGATE_RESULT);
            assertThat(result.podName()).isEqualTo("my-pod");
            assertThat(result.verdict()).isEqualTo("CRITICAL");
        });
    }
}
