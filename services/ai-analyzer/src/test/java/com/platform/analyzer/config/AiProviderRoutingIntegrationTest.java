package com.platform.analyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import com.platform.analyzer.domain.ports.PromptCalibrationStrategy;
import com.platform.analyzer.infrastructure.client.byok.ByokLanguageModelAdapter;
import com.platform.analyzer.infrastructure.client.byok.ByokPayloadMapper;
import com.platform.analyzer.infrastructure.client.byok.ByokResponseExtractor;
import com.platform.analyzer.infrastructure.client.ollama.OllamaLanguageModelAdapter;
import com.platform.analyzer.infrastructure.prompt.DefaultPromptCalibrationStrategy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for mutual exclusivity of AI provider beans.
 * Feature: dynamic-ai-provider-routing, Property 1: Mutual exclusivity of provider beans.
 */
@Tag("Feature: dynamic-ai-provider-routing, Property 1: Mutual exclusivity of provider beans")
class AiProviderRoutingIntegrationTest {

    @Configuration
    @EnableConfigurationProperties(PlatformProperties.class)
    @Import({OllamaConfig.class, AiProviderValidator.class})
    static class OllamaTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        PromptCalibrationStrategy promptCalibrationStrategy() {
            return new DefaultPromptCalibrationStrategy();
        }
    }

    @Configuration
    @EnableConfigurationProperties({PlatformProperties.class, ByokProperties.class})
    @Import({ByokConfig.class, AiProviderValidator.class})
    static class ByokTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        PromptCalibrationStrategy promptCalibrationStrategy() {
            return new DefaultPromptCalibrationStrategy();
        }
    }

    @Nested
    @SpringBootTest(classes = OllamaTestConfig.class)
    @TestPropertySource(properties = {
            "platform.ai.provider=ollama",
            "platform.storage.type=opensearch",
            "platform.messaging.type=kafka",
            "ollama.api.url=http://localhost:11434",
            "ollama.model=llama3.1"
    })
    class WhenOllamaProvider {

        @Autowired
        private ApplicationContext context;

        @Test
        void shouldRegisterExactlyOneAiLanguageModelPort() {
            var beans = context.getBeansOfType(AiLanguageModelPort.class);
            assertThat(beans).hasSize(1);
        }

        @Test
        void shouldRegisterOllamaAdapter() {
            var beans = context.getBeansOfType(AiLanguageModelPort.class);
            assertThat(beans.values().iterator().next())
                    .isInstanceOf(OllamaLanguageModelAdapter.class);
        }

        @Test
        void shouldNotRegisterByokBeans() {
            assertThat(context.getBeansOfType(ByokLanguageModelAdapter.class)).isEmpty();
            assertThat(context.getBeansOfType(ByokPayloadMapper.class)).isEmpty();
            assertThat(context.getBeansOfType(ByokResponseExtractor.class)).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(classes = ByokTestConfig.class)
    @TestPropertySource(properties = {
            "platform.ai.provider=byok",
            "platform.storage.type=opensearch",
            "platform.messaging.type=kafka",
            "platform.byok.endpoint=https://api.openai.com",
            "platform.byok.api-key=sk-test-123",
            "platform.byok.model=gpt-4",
            "platform.byok.provider-type=OPENAI_COMPATIBLE"
    })
    class WhenByokProvider {

        @Autowired
        private ApplicationContext context;

        @Test
        void shouldRegisterExactlyOneAiLanguageModelPort() {
            var beans = context.getBeansOfType(AiLanguageModelPort.class);
            assertThat(beans).hasSize(1);
        }

        @Test
        void shouldRegisterByokAdapter() {
            var beans = context.getBeansOfType(AiLanguageModelPort.class);
            assertThat(beans.values().iterator().next())
                    .isInstanceOf(ByokLanguageModelAdapter.class);
        }

        @Test
        void shouldNotRegisterOllamaBeans() {
            assertThat(context.getBeansOfType(OllamaLanguageModelAdapter.class)).isEmpty();
        }
    }
}
