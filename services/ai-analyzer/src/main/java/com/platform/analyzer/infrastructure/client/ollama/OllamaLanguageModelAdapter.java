package com.platform.analyzer.infrastructure.client.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import com.platform.analyzer.service.PromptTruncator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter implementing AiLanguageModelPort using Ollama as the AI provider.
 * Instantiated by {@link com.platform.analyzer.config.OllamaConfig} when platform.ai.provider=ollama.
 */
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
    private final String baseUrl;
    private final PromptTruncator promptTruncator;

    public OllamaLanguageModelAdapter(
            RestClient ollamaRestClient,
            ObjectMapper objectMapper,
            String model,
            String baseUrl) {
        this(ollamaRestClient, objectMapper, model, baseUrl, new PromptTruncator(65536));
    }

    public OllamaLanguageModelAdapter(
            RestClient ollamaRestClient,
            ObjectMapper objectMapper,
            String model,
            String baseUrl,
            PromptTruncator promptTruncator) {
        this.ollamaRestClient = ollamaRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.baseUrl = baseUrl;
        this.promptTruncator = promptTruncator;
    }

    @Override
    public AiAnalysis analyze(KubernetesEvent event, List<AiAnalysis> history, EnrichedContext context) {
        String prompt = buildPrompt(event, history, context);
        OllamaRequest request = new OllamaRequest(model, prompt, false);

        log.debug("Sending event for pod '{}' to Ollama model '{}' with {} history records, mcpContext={}",
                event.podName(), model, history.size(), context != null && context.hasContent());

        OllamaResponse ollamaResponse = callOllama(request);

        if (ollamaResponse == null || ollamaResponse.response() == null) {
            throw new AiAnalysisException(
                    "Ollama returned a null or empty response for pod: " + event.podName());
        }

        AiAnalysis parsed = parseAnalysis(ollamaResponse.response(), event.podName());

        boolean contextAvailable = context != null && context.hasContent();
        List<String> toolsUsed = contextAvailable ? context.toolsUsed() : List.of();

        return new AiAnalysis(
                parsed.podName(), parsed.namespace(), parsed.verdict(),
                parsed.rootCauseAnalysis(), parsed.recommendedActions(),
                toolsUsed, contextAvailable);
    }

    protected OllamaResponse callOllama(OllamaRequest request) {
        try {
            return ollamaRestClient
                    .post()
                    .uri("/api/generate")
                    .body(request)
                    .retrieve()
                    .body(OllamaResponse.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new AiAnalysisException(
                    "Ollama returned HTTP %d at '%s/api/generate'".formatted(
                            e.getStatusCode().value(), getBaseUrl()), e);
        } catch (ResourceAccessException e) {
            throw new AiAnalysisException(
                    "Cannot connect to Ollama at '%s': %s".formatted(
                            getBaseUrl(), e.getMessage()), e);
        }
    }

    private String getBaseUrl() {
        return baseUrl;
    }

    String buildPrompt(KubernetesEvent event, List<AiAnalysis> history, EnrichedContext context) {
        String historyContext;
        if (history == null || history.isEmpty()) {
            historyContext = "No previous analysis records found for this pod.";
        } else {
            historyContext = history.stream()
                    .map(a -> "  - Verdict: %s | Root Cause: %s".formatted(
                            a.verdict(), a.rootCauseAnalysis()))
                    .collect(Collectors.joining("\n"));
        }

        String basePrompt = SYSTEM_PROMPT_TEMPLATE.formatted(
                historyContext,
                event.podName(),
                event.namespace(),
                event.status(),
                event.timestamp()
        );

        if (context == null || !context.hasContent()) {
            return basePrompt;
        }

        int basePromptBytes = basePrompt.getBytes(StandardCharsets.UTF_8).length;
        EnrichedContext truncatedContext = promptTruncator.truncateIfNeeded(basePromptBytes, context);

        if (truncatedContext == null || !truncatedContext.hasContent()) {
            return basePrompt;
        }

        return basePrompt + "\n" + buildMcpContextSection(truncatedContext);
    }

    /**
     * Legacy overload for backward compatibility with existing tests.
     */
    String buildPrompt(KubernetesEvent event, List<AiAnalysis> history) {
        return buildPrompt(event, history, EnrichedContext.EMPTY);
    }

    static String buildMcpContextSection(EnrichedContext context) {
        var sb = new StringBuilder();
        sb.append("=== CLUSTER CONTEXT (MCP) ===\n");

        if (context.podDescription() != null) {
            sb.append("\n--- POD DESCRIPTION ---\n");
            sb.append(context.podDescription()).append("\n");
        }
        if (context.podEvents() != null) {
            sb.append("\n--- EVENTS ---\n");
            sb.append(context.podEvents()).append("\n");
        }
        if (context.podLogs() != null) {
            sb.append("\n--- LOGS ---\n");
            sb.append(context.podLogs()).append("\n");
        }

        return sb.toString();
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
