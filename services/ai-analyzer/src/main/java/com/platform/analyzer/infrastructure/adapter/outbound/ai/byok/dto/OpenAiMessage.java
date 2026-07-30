package com.platform.analyzer.infrastructure.adapter.outbound.ai.byok.dto;

/**
 * Represents a single message in the OpenAI chat completions messages array.
 */
public record OpenAiMessage(String role, String content) {}
