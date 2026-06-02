package com.platform.analyzer.infrastructure.client.byok.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response body from the OpenAI-compatible /v1/chat/completions endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiResponse(List<OpenAiChoice> choices) {}
