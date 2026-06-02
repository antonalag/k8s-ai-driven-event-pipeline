package com.platform.analyzer.infrastructure.client.byok;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.config.ByokProperties;
import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for ByokLanguageModelAdapter error handling.
 * Feature: dynamic-ai-provider-routing.
 */
@Tag("Feature: dynamic-ai-provider-routing")
class ByokAdapterPropertyTest {

    private final RestClient restClient = mock(RestClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 6: BYOK 4xx error wrapping with body truncation.
     * For any 4xx status and any response body, the adapter truncates body to ≤1024 chars.
     */
    @Property(tries = 100)
    @Tag("Property 6: BYOK 4xx error wrapping with body truncation")
    void http4xxShouldTruncateBodyTo1024Chars(
            @ForAll @IntRange(min = 400, max = 499) int statusCode,
            @ForAll @StringLength(min = 0, max = 5000) String responseBody) {

        var adapter = createAdapter("https://api.example.com");

        var exception = HttpClientErrorException.create(
                HttpStatusCode.valueOf(statusCode),
                "Client Error",
                HttpHeaders.EMPTY,
                responseBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        stubRestClientToThrow(exception);

        var event = sampleEvent("test-pod");

        AiAnalysisException thrown = catchThrowableOfType(
                () -> adapter.analyze(event, List.of()),
                AiAnalysisException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains(String.valueOf(statusCode));
        assertThat(thrown.getCause()).isEqualTo(exception);

        // Verify body truncation: message should contain at most 1024 chars of body
        String msgAfterCode = thrown.getMessage().substring(
                thrown.getMessage().indexOf(String.valueOf(statusCode)));
        if (responseBody.length() > 1024) {
            assertThat(msgAfterCode).doesNotContain(responseBody);
            // The truncated body (first 1024 chars) should be present
            assertThat(thrown.getMessage()).contains(responseBody.substring(0, 1024));
        }
    }

    /**
     * Property 7: BYOK network error wrapping preserves endpoint and message.
     * For any endpoint URL and network error, the adapter wraps with endpoint and error message.
     */
    @Property(tries = 100)
    @Tag("Property 7: BYOK network error wrapping preserves endpoint and message")
    void networkErrorShouldWrapWithEndpointAndMessage(
            @ForAll @StringLength(min = 5, max = 150) String endpoint,
            @ForAll @StringLength(min = 1, max = 200) String errorMessage) {

        var adapter = createAdapter(endpoint);
        var event = sampleEvent("test-pod");

        var cause = new ResourceAccessException(errorMessage, new IOException(errorMessage));
        stubRestClientToThrow(cause);

        AiAnalysisException thrown = catchThrowableOfType(
                () -> adapter.analyze(event, List.of()),
                AiAnalysisException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains(endpoint);
        assertThat(thrown.getMessage()).contains(errorMessage);
        assertThat(thrown.getCause()).isEqualTo(cause);
    }

    private ByokLanguageModelAdapter createAdapter(String endpoint) {
        var properties = new ByokProperties(endpoint, "sk-test-key", "gpt-4", ProviderType.OPENAI_COMPATIBLE);
        var payloadMapper = new ByokPayloadMapper();
        var responseExtractor = new ByokResponseExtractor(objectMapper);
        return new ByokLanguageModelAdapter(restClient, objectMapper, payloadMapper, responseExtractor, properties);
    }

    private void stubRestClientToThrow(RuntimeException exception) {
        var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        var requestBodySpec = mock(RestClient.RequestBodySpec.class);
        var responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(exception);
    }

    private KubernetesEvent sampleEvent(String podName) {
        return new KubernetesEvent(podName, "default", PodPhase.Running, Instant.now());
    }
}
