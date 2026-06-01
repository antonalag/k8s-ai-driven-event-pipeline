package com.platform.analyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class OllamaConfig {

    @Value("${ollama.api.url}")
    private String ollamaApiUrl;

    /**
     * Creates a {@link RestClient} instance pointing to the Ollama API base URL.
     * All Ollama service calls share this single, reusable client.
     *
     * @return a configured {@link RestClient}
     */
    @Bean
    public RestClient ollamaRestClient() {
        return RestClient.builder()
                .baseUrl(ollamaApiUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
