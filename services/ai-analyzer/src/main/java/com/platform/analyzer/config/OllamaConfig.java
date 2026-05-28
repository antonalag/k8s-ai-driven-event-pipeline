package com.platform.analyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration that exposes a {@link RestClient} bean pre-configured
 * to communicate with the local Ollama HTTP API.
 *
 * <p>The base URL is read from {@code ollama.api.url} in {@code application.properties}
 * and can be overridden at runtime via the {@code OLLAMA_API_URL} environment variable.
 */
@Configuration
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
