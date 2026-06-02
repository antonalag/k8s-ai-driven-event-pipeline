package com.platform.analyzer.infrastructure.client.byok.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a single choice in the OpenAI chat completions response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChoice(OpenAiMessage message) {}
