package com.platform.analyzer.infrastructure.client.ollama;

/**
 * DTO for the Ollama /api/generate endpoint request body.
 */
public record OllamaRequest(
        String model,
        String prompt,
        boolean stream
) {}
