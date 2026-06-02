package com.platform.analyzer.infrastructure.client.byok.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response body from a custom (Ollama-like) provider endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomProviderResponse(String response) {}
