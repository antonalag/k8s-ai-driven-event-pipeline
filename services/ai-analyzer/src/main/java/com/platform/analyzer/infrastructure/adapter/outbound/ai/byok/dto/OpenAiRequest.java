package com.platform.analyzer.infrastructure.adapter.outbound.ai.byok.dto;

import java.util.List;

/**
 * Request body for OpenAI-compatible /v1/chat/completions endpoint.
 */
public record OpenAiRequest(String model, List<OpenAiMessage> messages) {}
