package com.platform.analyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.model.AiAnalysis;
import com.platform.analyzer.model.KubernetesEvent;
import com.platform.analyzer.model.OllamaRequest;
import com.platform.analyzer.model.OllamaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Service responsible for sending a {@link KubernetesEvent} to the local Ollama
 * instance and parsing the structured {@link AiAnalysis} response.
 *
 * <h2>Prompt Strategy</h2>
 * <p>The system prompt follows a <em>role + constraint + schema + example</em> pattern:
 * <ol>
 *   <li><strong>Role:</strong> The model is told it is a Senior SRE/Kubernetes Engineer.
 *       This activates domain-specific reasoning and vocabulary.</li>
 *   <li><strong>Hard constraint:</strong> The model is explicitly forbidden from producing
 *       any text outside a single JSON object. No markdown fences, no preamble, no
 *       explanation — raw JSON only. This is the most critical instruction for reliable
 *       downstream parsing.</li>
 *   <li><strong>Schema embedding:</strong> The exact field names, types, and enum values
 *       from {@code specs/schemas/ai-analysis.v1.json} are inlined into the prompt so the
 *       model has a precise structural target, not just a vague instruction.</li>
 *   <li><strong>Input injection:</strong> The actual event data (podName, namespace, status,
 *       timestamp) is appended as the "user" section so the model can ground its analysis
 *       in real observed state rather than hallucinating.</li>
 * </ol>
 *
 * <p>The {@code /api/generate} endpoint is used with {@code stream=false} to receive
 * a single, complete response object — simpler and more reliable than streaming for
 * structured-output use cases.
 */
@Service
public class OllamaAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(OllamaAnalyzerService.class);

    // ── System prompt template ────────────────────────────────────────────────
    // Embedded schema rules ensure the model has a precise structural target.
    // The %s placeholders are filled at call time with the actual event fields.
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a Senior Site Reliability Engineer (SRE) and Kubernetes Expert.
            Your ONLY task is to analyse the Kubernetes Pod event provided below and \
            return a diagnosis.

            CRITICAL RULES — you MUST follow all of them without exception:
            1. Your entire response MUST be a single, valid JSON object. Nothing else.
            2. Do NOT include markdown code fences (no ```json or ```).
            3. Do NOT include any introductory text, explanation, or commentary.
            4. Do NOT add any fields beyond those listed in the schema below.
            5. All required fields MUST be present and non-empty.

            OUTPUT SCHEMA (you must conform to this exactly):
            {
              "podName":            "<string — copy from input>",
              "namespace":          "<string — copy from input>",
              "verdict":            "<one of: HEALTHY | TRANSIENT_ISSUE | CRITICAL_FAILURE>",
              "rootCauseAnalysis":  "<string — concise root cause, max 500 characters>",
              "recommendedActions": ["<action 1>", "<action 2>", ...]
            }

            VERDICT RULES:
            - HEALTHY          → Pod is Running or Succeeded with no anomalies.
            - TRANSIENT_ISSUE  → Pod is Pending or Unknown; likely a scheduling or \
            transient network issue.
            - CRITICAL_FAILURE → Pod is Failed; immediate intervention required.

            INPUT EVENT:
            - podName:   %s
            - namespace: %s
            - status:    %s
            - timestamp: %s

            Respond with the JSON object only.
            """;

    private final RestClient ollamaRestClient;
    private final ObjectMapper objectMapper;
    private final String model;

    /**
     * Constructor injection — preferred over field injection per project standards.
     *
     * @param ollamaRestClient pre-configured {@link RestClient} pointing to Ollama base URL
     * @param objectMapper     shared Jackson mapper for serialising requests and parsing responses
     * @param model            Ollama model name read from {@code ollama.model} property
     */
    public OllamaAnalyzerService(
            RestClient ollamaRestClient,
            ObjectMapper objectMapper,
            @Value("${ollama.model}") String model) {
        this.ollamaRestClient = ollamaRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    /**
     * Analyses a {@link KubernetesEvent} by calling the Ollama {@code /api/generate}
     * endpoint and parsing the structured JSON response into an {@link AiAnalysis}.
     *
     * @param event the Kubernetes Pod event to analyse
     * @return a structured {@link AiAnalysis} parsed from the model's response
     * @throws OllamaAnalysisException if the HTTP call fails or the response cannot be parsed
     */
    public AiAnalysis analyse(KubernetesEvent event) {
        String prompt = buildPrompt(event);
        OllamaRequest request = new OllamaRequest(model, prompt, false);

        log.debug("Sending event for pod '{}' to Ollama model '{}'", event.podName(), model);

        OllamaResponse ollamaResponse = ollamaRestClient
                .post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(OllamaResponse.class);

        if (ollamaResponse == null || ollamaResponse.response() == null) {
            throw new OllamaAnalysisException("Ollama returned a null or empty response for pod: " + event.podName());
        }

        return parseAnalysis(ollamaResponse.response(), event.podName());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildPrompt(KubernetesEvent event) {
        return SYSTEM_PROMPT_TEMPLATE.formatted(
                event.podName(),
                event.namespace(),
                event.status(),
                event.timestamp()
        );
    }

    /**
     * Parses the raw text returned by Ollama into an {@link AiAnalysis} record.
     *
     * <p>Some models occasionally wrap the JSON in markdown fences despite explicit
     * instructions. This method strips common fence patterns defensively before
     * attempting Jackson deserialization.
     */
    private AiAnalysis parseAnalysis(String rawResponse, String podName) {
        // Defensive strip of markdown fences that some models emit despite instructions
        String cleaned = rawResponse.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
        }

        try {
            return objectMapper.readValue(cleaned, AiAnalysis.class);
        } catch (JsonProcessingException e) {
            throw new OllamaAnalysisException(
                    "Failed to parse Ollama response as AiAnalysis for pod '%s'. Raw response: %s"
                            .formatted(podName, rawResponse), e);
        }
    }

    /**
     * Unchecked exception thrown when the Ollama call or response parsing fails.
     */
    public static class OllamaAnalysisException extends RuntimeException {
        public OllamaAnalysisException(String message) {
            super(message);
        }
        public OllamaAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
