package com.platform.analyzer.infrastructure.client.byok.dto;

/**
 * Request body for custom (Ollama-like) provider endpoints.
 */
public record CustomProviderRequest(String model, String prompt, boolean stream) {}
