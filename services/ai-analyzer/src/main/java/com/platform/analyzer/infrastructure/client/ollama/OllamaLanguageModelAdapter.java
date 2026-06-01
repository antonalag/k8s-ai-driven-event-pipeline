package com.platform.analyzer.infrastructure.client.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter implementing AiLanguageModelPort using Ollama as the AI provider.
 * Activated when platform.ai.provider=ollama.
 */
@Component
@ConditionalOnProperty(name = "platform.ai.provider", havingValue = "ollama")
public class OllamaLanguageModelAdapter implements AiLanguageModelPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaLanguageModelAdapter.class);

    static final String SYSTEM_PROMPT_TEMPLATE = """
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

            HISTORICAL CONTEXT (Previous diagnostic verdicts for this Pod, from newest to oldest):
            %s

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

    public OllamaLanguageModelAdapter(
            RestClient ollamaRestClient,
            ObjectMapper objectMapper,
            @Value("${ollama.model}") String model) {
        this.ollamaRestClient = ollamaRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public AiAnalysis analyze(KubernetesEvent event, List<AiAnalysis> history) {
        String prompt = buildPrompt(event, history);
        OllamaRequest request = new OllamaRequest(model, prompt, false);

        log.debug("Sending event for pod '{}' to Ollama model '{}' with {} history records",
                event.podName(), model, history.size());

        OllamaResponse ollamaResponse = callOllama(request);

        if (ollamaResponse == null || ollamaResponse.response() == null) {
            throw new AiAnalysisException(
                    "Ollama returned a null or empty response for pod: " + event.podName());
        }

        return parseAnalysis(ollamaResponse.response(), event.podName());
    }

    protected OllamaResponse callOllama(OllamaRequest request) {
        return ollamaRestClient
                .post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(OllamaResponse.class);
    }

    String buildPrompt(KubernetesEvent event, List<AiAnalysis> history) {
        String historyContext;
        if (history == null || history.isEmpty()) {
            historyContext = "No previous analysis records found for this pod.";
        } else {
            historyContext = history.stream()
                    .map(a -> "  - Verdict: %s | Root Cause: %s".formatted(
                            a.verdict(), a.rootCauseAnalysis()))
                    .collect(Collectors.joining("\n"));
        }

        return SYSTEM_PROMPT_TEMPLATE.formatted(
                historyContext,
                event.podName(),
                event.namespace(),
                event.status(),
                event.timestamp()
        );
    }

    AiAnalysis parseAnalysis(String rawResponse, String podName) {
        String cleaned = rawResponse.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
        }

        try {
            return objectMapper.readValue(cleaned, AiAnalysis.class);
        } catch (JsonProcessingException e) {
            throw new AiAnalysisException(
                    "Failed to parse Ollama response as AiAnalysis for pod '%s'. Raw response: %s"
                            .formatted(podName, rawResponse), e);
        }
    }
}
