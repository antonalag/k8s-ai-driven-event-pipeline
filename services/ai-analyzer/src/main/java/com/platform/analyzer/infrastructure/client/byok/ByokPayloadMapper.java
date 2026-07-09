package com.platform.analyzer.infrastructure.client.byok;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.ports.PromptCalibrationStrategy;
import com.platform.analyzer.infrastructure.client.byok.dto.CustomProviderRequest;
import com.platform.analyzer.infrastructure.client.byok.dto.OpenAiMessage;
import com.platform.analyzer.infrastructure.client.byok.dto.OpenAiRequest;
import com.platform.analyzer.service.PromptTruncator;

import java.nio.charset.StandardCharsets;
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
              "recommendedActions": ["<kubectl command 1>", "<kubectl command 2>", ...]
            }

            RECOMMENDED ACTIONS RULES:
            - Each action MUST be a complete, copy-pasteable shell command (kubectl or standard CLI).
            - Do NOT include descriptions, explanations, or prose — ONLY executable commands.
            - Do NOT use placeholders like <node_IP> or <configmap-name>. Use actual resource names from the input.
            - Provide 1 to 5 actions maximum, ordered by diagnostic priority.
            - PRIORITIZE mutation commands that fix the root cause:
              * For ImagePullBackOff: use "kubectl set image deployment/<name> <container-name>=<correct-image> -n <namespace>"
                IMPORTANT: <container-name> is the NAME of the container (e.g., "app", "main", "nginx"), NOT the old image reference.
                The container name can be found in the MCP context under containers[].name.
                Example: kubectl set image deployment/my-app app=nginx:1.27-alpine -n my-namespace
              * For CrashLoopBackOff: use "kubectl rollout restart deployment/<name> -n <namespace>"
              * For scaling issues: use "kubectl scale deployment/<name> --replicas=<N> -n <namespace>"
            - Diagnostic commands (describe, get events, logs) should come AFTER the fix command.

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

    private final PromptTruncator promptTruncator;
    private final PromptCalibrationStrategy promptCalibrationStrategy;

    public ByokPayloadMapper() {
        this(new PromptTruncator(65536), null);
    }

    public ByokPayloadMapper(PromptTruncator promptTruncator) {
        this(promptTruncator, null);
    }

    public ByokPayloadMapper(PromptTruncator promptTruncator, PromptCalibrationStrategy promptCalibrationStrategy) {
        this.promptTruncator = promptTruncator;
        this.promptCalibrationStrategy = promptCalibrationStrategy;
    }

    /**
     * Builds the request body for the given provider type with enriched MCP context.
     *
     * @return an {@link OpenAiRequest} or {@link CustomProviderRequest} depending on the provider type
     */
    public Object buildRequestBody(
            KubernetesEvent event,
            List<AiAnalysis> history,
            EnrichedContext context,
            String model,
            ProviderType providerType) {

        String historyContext = formatHistoryContext(history);
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(historyContext);
        String userContent = formatUserContent(event);

        if (promptCalibrationStrategy != null) {
            String calibration = promptCalibrationStrategy.buildCalibratedPrompt(event, context);
            if (calibration != null && !calibration.isBlank()) {
                userContent = userContent + calibration;
            }
        }

        if (context != null && context.hasContent()) {
            int basePromptBytes = (systemPrompt + "\n" + userContent).getBytes(StandardCharsets.UTF_8).length;
            EnrichedContext truncatedContext = promptTruncator.truncateIfNeeded(basePromptBytes, context);
            if (truncatedContext != null && truncatedContext.hasContent()) {
                userContent = userContent + "\n" + buildMcpContextSection(truncatedContext);
            }
        }

        return switch (providerType) {
            case OPENAI_COMPATIBLE -> new OpenAiRequest(model, List.of(
                    new OpenAiMessage("system", systemPrompt),
                    new OpenAiMessage("user", userContent)
            ));
            case CUSTOM -> new CustomProviderRequest(model, systemPrompt + "\n" + userContent, false);
        };
    }

    /**
     * Legacy overload for backward compatibility.
     */
    public Object buildRequestBody(
            KubernetesEvent event,
            List<AiAnalysis> history,
            String model,
            ProviderType providerType) {
        return buildRequestBody(event, history, EnrichedContext.EMPTY, model, providerType);
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
}
