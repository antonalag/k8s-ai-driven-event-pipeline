package com.platform.analyzer.infrastructure.client.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for the Ollama /api/generate endpoint response body.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaResponse(String response) {}
