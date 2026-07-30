package com.platform.analyzer.infrastructure.adapter.outbound.ai.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for the Ollama /api/generate endpoint response body.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaResponse(String response) {}
