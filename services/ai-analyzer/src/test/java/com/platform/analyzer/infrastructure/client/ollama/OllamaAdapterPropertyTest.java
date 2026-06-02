package com.platform.analyzer.infrastructure.client.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for OllamaLanguageModelAdapter error handling.
 * Feature: dynamic-ai-provider-routing.
 */
@Tag("Feature: dynamic-ai-provider-routing")
class OllamaAdapterPropertyTest {

    private final RestClient restClient = mock(RestClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 3: Ollama network error wrapping preserves context.
     * For any URL and ResourceAccessException, the adapter wraps it in AiAnalysisException
     * containing the URL and original cause.
     */
    @Property(tries = 100)
    @Tag("Property 3: Ollama network error wrapping preserves context")
    void networkErrorShouldWrapWithUrlAndCause(
            @ForAll @StringLength(min = 5, max = 100) String baseUrl,
            @ForAll @StringLength(min = 1, max = 200) String errorMessage) {

        var adapter = createAdapter(baseUrl);
        var event = sampleEvent("test-pod");

        var cause = new ResourceAccessException(errorMessage, new IOException(errorMessage));

        // Stub the RestClient chain to throw ResourceAccessException
        var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        var requestBodySpec = mock(RestClient.RequestBodySpec.class);
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(cause);

        AiAnalysisException thrown = catchThrowableOfType(
                () -> adapter.analyze(event, List.of()),
                AiAnalysisException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains(baseUrl);
        assertThat(thrown.getCause()).isEqualTo(cause);
    }

    /**
     * Property 4: Ollama HTTP error wrapping preserves status code and URL.
     * For any HTTP error status (4xx/5xx), the adapter wraps it with code and URL.
     */
    @Property(tries = 100)
    @Tag("Property 4: Ollama HTTP error wrapping preserves status code and URL")
    void httpErrorShouldWrapWithStatusCodeAndUrl(
            @ForAll @IntRange(min = 400, max = 599) int statusCode,
            @ForAll @StringLength(min = 5, max = 100) String baseUrl) {

        var adapter = createAdapter(baseUrl);
        var event = sampleEvent("test-pod");

        RuntimeException httpException;
        if (statusCode < 500) {
            httpException = HttpClientErrorException.create(
                    HttpStatusCode.valueOf(statusCode), "error", HttpHeaders.EMPTY, new byte[0], null);
        } else {
            httpException = HttpServerErrorException.create(
                    HttpStatusCode.valueOf(statusCode), "error", HttpHeaders.EMPTY, new byte[0], null);
        }

        var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        var requestBodySpec = mock(RestClient.RequestBodySpec.class);
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(httpException);

        AiAnalysisException thrown = catchThrowableOfType(
                () -> adapter.analyze(event, List.of()),
                AiAnalysisException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains(String.valueOf(statusCode));
        assertThat(thrown.getMessage()).contains(baseUrl);
        assertThat(thrown.getCause()).isEqualTo(httpException);
    }

    /**
     * Property 5: Ollama null/empty response includes pod name.
     * For any pod name, when Ollama returns null, the adapter throws AiAnalysisException
     * containing the pod name.
     */
    @Property(tries = 100)
    @Tag("Property 5: Ollama null/empty response includes pod name")
    void nullResponseShouldThrowWithPodName(
            @ForAll @StringLength(min = 1, max = 80) String podName) {

        var adapter = createAdapterReturningNull();
        var event = sampleEvent(podName);

        AiAnalysisException thrown = catchThrowableOfType(
                () -> adapter.analyze(event, List.of()),
                AiAnalysisException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains(podName);
    }

    private OllamaLanguageModelAdapter createAdapter(String baseUrl) {
        return new OllamaLanguageModelAdapter(restClient, objectMapper, "llama3.1", baseUrl);
    }

    private OllamaLanguageModelAdapter createAdapterReturningNull() {
        return new OllamaLanguageModelAdapter(restClient, objectMapper, "llama3.1", "http://localhost:11434") {
            @Override
            protected OllamaResponse callOllama(OllamaRequest request) {
                return null;
            }
        };
    }

    private KubernetesEvent sampleEvent(String podName) {
        return new KubernetesEvent(podName, "default", PodPhase.Running, Instant.now());
    }
}
