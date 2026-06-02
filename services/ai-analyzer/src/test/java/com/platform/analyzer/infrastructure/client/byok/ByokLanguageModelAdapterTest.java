package com.platform.analyzer.infrastructure.client.byok;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.config.ByokProperties;
import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ByokLanguageModelAdapterTest {

    private RestClient restClient;
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;
    private ByokPayloadMapper payloadMapper;
    private ByokResponseExtractor responseExtractor;
    private ObjectMapper objectMapper;
    private ByokProperties properties;
    private ByokLanguageModelAdapter adapter;

    private static final KubernetesEvent SAMPLE_EVENT = new KubernetesEvent(
            "crash-pod", "production", PodPhase.Failed, Instant.parse("2025-06-01T10:00:00Z"));

    private static final String VALID_JSON = """
            {"podName":"crash-pod","namespace":"production","verdict":"CRITICAL_FAILURE","rootCauseAnalysis":"OOMKilled","recommendedActions":["Increase memory limit"]}""";

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        payloadMapper = mock(ByokPayloadMapper.class);
        responseExtractor = mock(ByokResponseExtractor.class);
        objectMapper = new ObjectMapper();
        properties = new ByokProperties(
                "https://api.openai.com", "sk-test-key", "gpt-4o-mini", ProviderType.OPENAI_COMPATIBLE);
        adapter = new ByokLanguageModelAdapter(
                restClient, objectMapper, payloadMapper, responseExtractor, properties);
    }

    private void stubFullChain(String responseBody) {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(responseBody);
    }

    private void stubChainUntilRetrieveThrows(Exception ex) {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(ex);
    }

    @Test
    void analyze_successfulInvocation_returnsAiAnalysis() {
        when(payloadMapper.buildRequestBody(any(), any(), anyString(), any()))
                .thenReturn("mock-body");
        stubFullChain("raw-provider-response");
        when(responseExtractor.extractContent(anyString(), any()))
                .thenReturn(VALID_JSON);

        AiAnalysis result = adapter.analyze(SAMPLE_EVENT, Collections.emptyList());

        assertThat(result.podName()).isEqualTo("crash-pod");
        assertThat(result.verdict()).isEqualTo("CRITICAL_FAILURE");
        assertThat(result.rootCauseAnalysis()).isEqualTo("OOMKilled");
        assertThat(result.recommendedActions()).containsExactly("Increase memory limit");
    }

    @Test
    void analyze_httpClientError_throwsAiAnalysisException() {
        when(payloadMapper.buildRequestBody(any(), any(), anyString(), any()))
                .thenReturn("mock-body");
        stubChainUntilRetrieveThrows(
                new HttpClientErrorException(HttpStatusCode.valueOf(401), "Unauthorized",
                        "Invalid API key".getBytes(), null));

        assertThatThrownBy(() -> adapter.analyze(SAMPLE_EVENT, Collections.emptyList()))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    void analyze_httpServerError_throwsAiAnalysisException() {
        when(payloadMapper.buildRequestBody(any(), any(), anyString(), any()))
                .thenReturn("mock-body");
        stubChainUntilRetrieveThrows(
                new HttpServerErrorException(HttpStatusCode.valueOf(503), "Service Unavailable",
                        null, null));

        assertThatThrownBy(() -> adapter.analyze(SAMPLE_EVENT, Collections.emptyList()))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("server failure HTTP 503");
    }

    @Test
    void analyze_networkError_throwsAiAnalysisException() {
        when(payloadMapper.buildRequestBody(any(), any(), anyString(), any()))
                .thenReturn("mock-body");
        stubChainUntilRetrieveThrows(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> adapter.analyze(SAMPLE_EVENT, Collections.emptyList()))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("Network error")
                .hasMessageContaining("api.openai.com");
    }

    @Test
    void analyze_nullResponseFromExtractor_throwsAiAnalysisException() {
        when(payloadMapper.buildRequestBody(any(), any(), anyString(), any()))
                .thenReturn("mock-body");
        stubFullChain("raw-provider-response");
        when(responseExtractor.extractContent(anyString(), any()))
                .thenThrow(new AiAnalysisException("BYOK provider returned a null or empty response body"));

        assertThatThrownBy(() -> adapter.analyze(SAMPLE_EVENT, Collections.emptyList()))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("null or empty response body");
    }

    @Test
    void parseAnalysis_responseWithMarkdownFences_parsesCorrectly() {
        String fencedResponse = "```json\n" + VALID_JSON + "\n```";

        AiAnalysis result = adapter.parseAnalysis(fencedResponse, "crash-pod");

        assertThat(result.podName()).isEqualTo("crash-pod");
        assertThat(result.verdict()).isEqualTo("CRITICAL_FAILURE");
    }

    @Test
    void parseAnalysis_responseWithWhitespace_parsesCorrectly() {
        String paddedResponse = "   \n" + VALID_JSON + "\n   ";

        AiAnalysis result = adapter.parseAnalysis(paddedResponse, "crash-pod");

        assertThat(result.podName()).isEqualTo("crash-pod");
    }

    @Test
    void parseAnalysis_malformedJson_throwsAiAnalysisException() {
        assertThatThrownBy(() -> adapter.parseAnalysis("not valid json {{{", "crash-pod"))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("crash-pod")
                .hasMessageContaining("not valid json");
    }
}
