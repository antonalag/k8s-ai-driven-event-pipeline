package com.platform.analyzer.infrastructure.client.byok;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.infrastructure.client.byok.dto.CustomProviderRequest;
import com.platform.analyzer.infrastructure.client.byok.dto.OpenAiMessage;
import com.platform.analyzer.infrastructure.client.byok.dto.OpenAiRequest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the HTTP request body for the configured BYOK provider type.
 * Reuses the same SRE system prompt template as the Ollama adapter for output consistency.
 */
public class ByokPayloadMapper {

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
            """;

    static final String USER_CONTENT_TEMPLATE = """
            INPUT EVENT:
            - podName:   %s
            - namespace: %s
            - status:    %s
            - timestamp: %s

            Respond with the JSON object only.
            """;

    /**
     * Builds the request body for the given provider type.
     *
     * @return an {@link OpenAiRequest} or {@link CustomProviderRequest} depending on the provider type
     */
    public Object buildRequestBody(
            KubernetesEvent event,
            List<AiAnalysis> history,
            String model,
            ProviderType providerType) {

        String historyContext = formatHistoryContext(history);
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(historyContext);
        String userContent = formatUserContent(event);

        return switch (providerType) {
            case OPENAI_COMPATIBLE -> new OpenAiRequest(model, List.of(
                    new OpenAiMessage("system", systemPrompt),
                    new OpenAiMessage("user", userContent)
            ));
            case CUSTOM -> new CustomProviderRequest(model, systemPrompt + "\n" + userContent, false);
        };
    }

    String formatHistoryContext(List<AiAnalysis> history) {
        if (history == null || history.isEmpty()) {
            return "No previous analysis records found for this pod.";
        }
        return history.stream()
                .map(a -> "  - Verdict: %s | Root Cause: %s".formatted(
                        a.verdict(), a.rootCauseAnalysis()))
                .collect(Collectors.joining("\n"));
    }

    String formatUserContent(KubernetesEvent event) {
        return USER_CONTENT_TEMPLATE.formatted(
                event.podName(),
                event.namespace(),
                event.status(),
                event.timestamp()
        );
    }
}
