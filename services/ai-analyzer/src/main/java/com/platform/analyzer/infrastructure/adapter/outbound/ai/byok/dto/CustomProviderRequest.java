package com.platform.analyzer.infrastructure.adapter.outbound.ai.byok.dto;

/**
 * Request body for custom (Ollama-like) provider endpoints.
 */
public record CustomProviderRequest(String model, String prompt, boolean stream) {}
