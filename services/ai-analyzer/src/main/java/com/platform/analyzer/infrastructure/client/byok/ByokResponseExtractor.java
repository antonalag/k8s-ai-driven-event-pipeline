package com.platform.analyzer.infrastructure.client.byok;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import com.platform.analyzer.infrastructure.client.byok.dto.CustomProviderResponse;
import com.platform.analyzer.infrastructure.client.byok.dto.OpenAiResponse;
import org.springframework.stereotype.Component;

/**
 * Extracts the raw AI text content from the provider's HTTP response body
 * based on the configured {@link ProviderType}.
 */
@Component
public class ByokResponseExtractor {

    private final ObjectMapper objectMapper;

    public ByokResponseExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts the AI-generated text from the raw HTTP response body.
     *
     * @param responseBody raw JSON string from the provider
     * @param providerType determines the extraction path
     * @return the AI-generated text content
     * @throws AiAnalysisException if the body is null/empty or the expected JSON path is missing
     */
    public String extractContent(String responseBody, ProviderType providerType) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new AiAnalysisException(
                    "BYOK provider returned a null or empty response body");
        }

        return switch (providerType) {
            case OPENAI_COMPATIBLE -> extractOpenAiContent(responseBody);
            case CUSTOM -> extractCustomContent(responseBody);
        };
    }

    private String extractOpenAiContent(String body) {
        try {
            OpenAiResponse response = objectMapper.readValue(body, OpenAiResponse.class);
            if (response.choices() == null || response.choices().isEmpty()) {
                throw new AiAnalysisException(
                        "BYOK OPENAI_COMPATIBLE response missing 'choices' array or empty");
            }
            var firstChoice = response.choices().getFirst();
            if (firstChoice.message() == null || firstChoice.message().content() == null) {
                throw new AiAnalysisException(
                        "BYOK OPENAI_COMPATIBLE response missing 'choices[0].message.content'");
            }
            return firstChoice.message().content();
        } catch (JsonProcessingException e) {
            throw new AiAnalysisException(
                    "Failed to parse BYOK OPENAI_COMPATIBLE response body: " + e.getMessage(), e);
        }
    }

    private String extractCustomContent(String body) {
        try {
            CustomProviderResponse response = objectMapper.readValue(body, CustomProviderResponse.class);
            if (response.response() == null) {
                throw new AiAnalysisException(
                        "BYOK CUSTOM response missing 'response' field");
            }
            return response.response();
        } catch (JsonProcessingException e) {
            throw new AiAnalysisException(
                    "Failed to parse BYOK CUSTOM response body: " + e.getMessage(), e);
        }
    }
}
