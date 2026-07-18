package com.platform.analyzer.infrastructure.client.byok;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.config.ByokProperties;
import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * BYOK (Bring Your Own Key) adapter for external AI providers.
 * Supports OpenAI-compatible and custom (Ollama-like) endpoints.
 * Instantiated by {@link com.platform.analyzer.config.ByokConfig} when platform.ai.provider=byok.
 */
public class ByokLanguageModelAdapter implements AiLanguageModelPort {

    private static final Logger log = LoggerFactory.getLogger(ByokLanguageModelAdapter.class);

    private final RestClient byokRestClient;
    private final ObjectMapper objectMapper;
    private final ByokPayloadMapper payloadMapper;
    private final ByokResponseExtractor responseExtractor;
    private final ByokProperties properties;

    public ByokLanguageModelAdapter(
            RestClient byokRestClient,
            ObjectMapper objectMapper,
            ByokPayloadMapper payloadMapper,
            ByokResponseExtractor responseExtractor,
            ByokProperties properties) {
        this.byokRestClient = byokRestClient;
        this.objectMapper = objectMapper;
        this.payloadMapper = payloadMapper;
        this.responseExtractor = responseExtractor;
        this.properties = properties;
    }

    @Override
    public AiAnalysis analyze(KubernetesEvent event, List<AiAnalysis> history, EnrichedContext context) {
        log.debug("BYOK analysis for pod '{}' using model '{}' with {} history records, mcpContext={}",
                event.podName(), properties.model(), history.size(),
                context != null && context.hasContent());

        Object requestBody = payloadMapper.buildRequestBody(
                event, history, context, properties.model(), properties.providerType());

        String responseBody = callProvider(requestBody);

        String rawContent = responseExtractor.extractContent(
                responseBody, properties.providerType());

        AiAnalysis parsed = parseAnalysis(rawContent, event.podName());

        boolean contextAvailable = context != null && context.hasContent();
        List<String> toolsUsed = contextAvailable ? context.toolsUsed() : List.of();

        return new AiAnalysis(
                parsed.podName(), parsed.namespace(), parsed.verdict(),
                parsed.rootCauseAnalysis(), parsed.recommendedActions(),
                toolsUsed, contextAvailable, properties.model());
    }

    protected String callProvider(Object requestBody) {
        try {
            return byokRestClient
                    .post()
                    .uri(resolveUri())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            if (body.length() > 1024) {
                body = body.substring(0, 1024);
            }
            throw new AiAnalysisException(
                    "BYOK provider returned HTTP %d: %s".formatted(
                            e.getStatusCode().value(), body), e);
        } catch (HttpServerErrorException e) {
            throw new AiAnalysisException(
                    "BYOK provider server failure HTTP %d".formatted(
                            e.getStatusCode().value()), e);
        } catch (ResourceAccessException e) {
            throw new AiAnalysisException(
                    "Network error calling BYOK provider at '%s': %s".formatted(
                            properties.endpoint(), e.getMessage()), e);
        }
    }

    private String resolveUri() {
        return switch (properties.providerType()) {
            case OPENAI_COMPATIBLE -> "/v1/chat/completions";
            case CUSTOM -> "/api/generate";
        };
    }

    AiAnalysis parseAnalysis(String rawResponse, String podName) {
        String cleaned = rawResponse.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "")
                    .replaceAll("```$", "").strip();
        }
        try {
            return objectMapper.readValue(cleaned, AiAnalysis.class);
        } catch (JsonProcessingException e) {
            throw new AiAnalysisException(
                    "Failed to parse BYOK response as AiAnalysis for pod '%s'. Raw: %s"
                            .formatted(podName, rawResponse), e);
        }
    }
}
