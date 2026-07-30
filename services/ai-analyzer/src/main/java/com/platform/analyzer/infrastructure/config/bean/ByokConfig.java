package com.platform.analyzer.infrastructure.config.bean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.domain.port.outbound.AiLanguageModelPort;
import com.platform.analyzer.domain.port.outbound.PromptCalibrationStrategy;
import com.platform.analyzer.infrastructure.adapter.outbound.ai.byok.ByokLanguageModelAdapter;
import com.platform.analyzer.infrastructure.adapter.outbound.ai.byok.ByokPayloadMapper;
import com.platform.analyzer.infrastructure.adapter.outbound.ai.byok.ByokResponseExtractor;
import com.platform.analyzer.infrastructure.config.properties.ByokProperties;
import com.platform.analyzer.infrastructure.config.properties.McpProperties;
import com.platform.analyzer.domain.service.PromptTruncator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for the BYOK AI provider.
 * Only active when {@code platform.ai.provider=byok}.
 */
@Configuration
@ConditionalOnProperty(name = "platform.ai.provider", havingValue = "byok")
@EnableConfigurationProperties({ByokProperties.class, McpProperties.class})
public class ByokConfig {

    @Bean
    RestClient byokRestClient(ByokProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.endpoint())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Bean
    ByokPayloadMapper byokPayloadMapper(McpProperties mcpProperties, PromptCalibrationStrategy promptCalibrationStrategy) {
        PromptTruncator truncator = new PromptTruncator(mcpProperties.maxPromptBytes());
        return new ByokPayloadMapper(truncator, promptCalibrationStrategy);
    }

    @Bean
    ByokResponseExtractor byokResponseExtractor(ObjectMapper objectMapper) {
        return new ByokResponseExtractor(objectMapper);
    }

    @Bean
    AiLanguageModelPort aiLanguageModelPort(
            RestClient byokRestClient,
            ObjectMapper objectMapper,
            ByokPayloadMapper payloadMapper,
            ByokResponseExtractor responseExtractor,
            ByokProperties properties) {
        return new ByokLanguageModelAdapter(
                byokRestClient, objectMapper, payloadMapper, responseExtractor, properties);
    }
}
