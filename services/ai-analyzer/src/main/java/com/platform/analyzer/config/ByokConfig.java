package com.platform.analyzer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for the BYOK AI provider.
 * Creates a dedicated {@link RestClient} bean with Bearer authentication header.
 * Only active when {@code platform.ai.provider=byok}.
 */
@Configuration
@ConditionalOnProperty(name = "platform.ai.provider", havingValue = "byok")
@EnableConfigurationProperties(ByokProperties.class)
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
}
