package com.platform.analyzer.model;

/**
 * DTO for the Ollama {@code /api/generate} endpoint request body.
 *
 * <p>Setting {@code stream} to {@code false} makes Ollama return a single
 * JSON response object instead of a newline-delimited stream, which simplifies
 * synchronous parsing.
 *
 * @param model  The Ollama model identifier (e.g. {@code llama3.1}).
 * @param prompt The full prompt string (system instructions + user content).
 * @param stream Whether to stream the response. Always {@code false} here.
 */
public record OllamaRequest(
        String model,
        String prompt,
        boolean stream
) {}
