package com.platform.analyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import com.platform.analyzer.domain.ports.PromptCalibrationStrategy;
import com.platform.analyzer.infrastructure.client.ollama.OllamaLanguageModelAdapter;
import com.platform.analyzer.service.PromptTruncator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for the Ollama AI provider.
 * Only active when {@code platform.ai.provider=ollama}.
 */
@Configuration
@ConditionalOnProperty(name = "platform.ai.provider", havingValue = "ollama")
@EnableConfigurationProperties({PlatformProperties.class, McpProperties.class})
public class OllamaConfig {

    @Value("${ollama.api.url}")
    private String ollamaApiUrl;

    @Bean
    RestClient ollamaRestClient() {
        return RestClient.builder()
                .baseUrl(ollamaApiUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    AiLanguageModelPort aiLanguageModelPort(
            RestClient ollamaRestClient,
            ObjectMapper objectMapper,
            McpProperties mcpProperties,
            PromptCalibrationStrategy promptCalibrationStrategy,
            @Value("${ollama.model}") String model) {
        PromptTruncator truncator = new PromptTruncator(mcpProperties.maxPromptBytes());
        return new OllamaLanguageModelAdapter(
                ollamaRestClient, objectMapper, model, ollamaApiUrl, truncator, promptCalibrationStrategy);
    }
}
