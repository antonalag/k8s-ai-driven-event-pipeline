package com.platform.analyzer.infrastructure.client.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OllamaLanguageModelAdapterTest {

    private ObjectMapper objectMapper;

    private static final String MODEL = "llama3.1";
    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-15T10:30:00Z");
    private static final List<AiAnalysis> EMPTY_HISTORY = Collections.emptyList();

    private static final KubernetesEvent FAILED_POD_EVENT = new KubernetesEvent(
            "payment-service-7d9f8b-xkp2q",
            "production",
            PodPhase.Failed,
            FIXED_INSTANT
    );

    private static final String VALID_JSON_RESPONSE = """
            {
              "podName": "payment-service-7d9f8b-xkp2q",
              "namespace": "production",
              "verdict": "CRITICAL_FAILURE",
              "rootCauseAnalysis": "Pod entered Failed phase due to OOMKilled — container exceeded memory limits.",
              "recommendedActions": [
                "Increase memory limits in the Pod spec",
                "Check application for memory leaks",
                "Review recent deployments for regressions"
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private OllamaLanguageModelAdapter adapterWithMockedResponse(OllamaResponse response) {
        return new OllamaLanguageModelAdapter(mock(RestClient.class), objectMapper, MODEL) {
            @Override
            protected OllamaResponse callOllama(OllamaRequest request) {
                return response;
            }
        };
    }

    @Nested
    @DisplayName("buildPrompt()")
    class BuildPromptTests {

        private OllamaLanguageModelAdapter adapter;

        @BeforeEach
        void setUp() {
            adapter = new OllamaLanguageModelAdapter(mock(RestClient.class), objectMapper, MODEL);
        }

        @Test
        @DisplayName("should inject podName into the prompt")
        void shouldInjectPodName() {
            String prompt = adapter.buildPrompt(FAILED_POD_EVENT, EMPTY_HISTORY);
            assertThat(prompt).contains("payment-service-7d9f8b-xkp2q");
        }

        @Test
        @DisplayName("should inject namespace into the prompt")
        void shouldInjectNamespace() {
            String prompt = adapter.buildPrompt(FAILED_POD_EVENT, EMPTY_HISTORY);
            assertThat(prompt).contains("production");
        }

        @Test
        @DisplayName("should inject pod status into the prompt")
        void shouldInjectStatus() {
            String prompt = adapter.buildPrompt(FAILED_POD_EVENT, EMPTY_HISTORY);
            assertThat(prompt).contains("Failed");
        }

        @Test
        @DisplayName("should inject timestamp into the prompt")
        void shouldInjectTimestamp() {
            String prompt = adapter.buildPrompt(FAILED_POD_EVENT, EMPTY_HISTORY);
            assertThat(prompt).contains(FIXED_INSTANT.toString());
        }

        @Test
        @DisplayName("should include the JSON schema contract in the prompt")
        void shouldIncludeSchemaContract() {
            String prompt = adapter.buildPrompt(FAILED_POD_EVENT, EMPTY_HISTORY);
            assertThat(prompt)
                    .contains("HEALTHY")
                    .contains("TRANSIENT_ISSUE")
                    .contains("CRITICAL_FAILURE")
                    .contains("rootCauseAnalysis")
                    .contains("recommendedActions");
        }

        @Test
        @DisplayName("should forbid markdown fences in the prompt instructions")
        void shouldForbidMarkdownFencesInInstructions() {
            String prompt = adapter.buildPrompt(FAILED_POD_EVENT, EMPTY_HISTORY);
            assertThat(prompt).contains("Do NOT include markdown code fences");
        }

        @Test
        @DisplayName("should include 'No previous analysis' when history is empty")
        void shouldIncludeNoHistoryMessage() {
            String prompt = adapter.buildPrompt(FAILED_POD_EVENT, EMPTY_HISTORY);
            assertThat(prompt).contains("No previous analysis records found for this pod.");
        }

        @Test
        @DisplayName("should include history entries when history is not empty")
        void shouldIncludeHistoryEntries() {
            List<AiAnalysis> history = List.of(
                    new AiAnalysis("payment-service-7d9f8b-xkp2q", "production",
                            "TRANSIENT_ISSUE", "Scheduling delay", List.of("Wait")));

            String prompt = adapter.buildPrompt(FAILED_POD_EVENT, history);
            assertThat(prompt).contains("Verdict: TRANSIENT_ISSUE");
            assertThat(prompt).contains("Root Cause: Scheduling delay");
        }
    }

    @Nested
    @DisplayName("parseAnalysis() — defensive markdown stripping")
    class MarkdownStrippingTests {

        private OllamaLanguageModelAdapter adapter;

        @BeforeEach
        void setUp() {
            adapter = new OllamaLanguageModelAdapter(mock(RestClient.class), objectMapper, MODEL);
        }

        @Test
        @DisplayName("should parse clean JSON without fences")
        void shouldParseCleanJson() {
            AiAnalysis result = adapter.parseAnalysis(VALID_JSON_RESPONSE, "payment-service-7d9f8b-xkp2q");

            assertThat(result.podName()).isEqualTo("payment-service-7d9f8b-xkp2q");
            assertThat(result.namespace()).isEqualTo("production");
            assertThat(result.verdict()).isEqualTo("CRITICAL_FAILURE");
            assertThat(result.rootCauseAnalysis()).contains("OOMKilled");
            assertThat(result.recommendedActions()).hasSize(3);
        }

        @Test
        @DisplayName("should strip ```json ... ``` fences and parse correctly")
        void shouldStripJsonFences() {
            String fencedResponse = "```json\n" + VALID_JSON_RESPONSE.strip() + "\n```";

            AiAnalysis result = adapter.parseAnalysis(fencedResponse, "payment-service-7d9f8b-xkp2q");

            assertThat(result.verdict()).isEqualTo("CRITICAL_FAILURE");
            assertThat(result.podName()).isEqualTo("payment-service-7d9f8b-xkp2q");
        }

        @Test
        @DisplayName("should strip plain ``` ... ``` fences and parse correctly")
        void shouldStripPlainFences() {
            String fencedResponse = "```\n" + VALID_JSON_RESPONSE.strip() + "\n```";

            AiAnalysis result = adapter.parseAnalysis(fencedResponse, "payment-service-7d9f8b-xkp2q");

            assertThat(result.verdict()).isEqualTo("CRITICAL_FAILURE");
        }

        @Test
        @DisplayName("should handle leading/trailing whitespace around JSON")
        void shouldHandleWhitespace() {
            String paddedResponse = "   \n  " + VALID_JSON_RESPONSE + "  \n  ";

            AiAnalysis result = adapter.parseAnalysis(paddedResponse, "payment-service-7d9f8b-xkp2q");

            assertThat(result.verdict()).isEqualTo("CRITICAL_FAILURE");
        }

        @Test
        @DisplayName("should ignore unknown fields in the JSON response (defensive)")
        void shouldIgnoreUnknownFields() {
            String responseWithExtraField = """
                    {
                      "podName": "payment-service-7d9f8b-xkp2q",
                      "namespace": "production",
                      "verdict": "CRITICAL_FAILURE",
                      "rootCauseAnalysis": "OOMKilled",
                      "recommendedActions": ["Increase memory limits"],
                      "unexpectedField": "this should be ignored"
                    }
                    """;

            AiAnalysis result = adapter.parseAnalysis(responseWithExtraField, "payment-service-7d9f8b-xkp2q");

            assertThat(result.verdict()).isEqualTo("CRITICAL_FAILURE");
            assertThat(result.recommendedActions()).containsExactly("Increase memory limits");
        }
    }

    @Nested
    @DisplayName("analyze() — exception handling")
    class ExceptionHandlingTests {

        private OllamaLanguageModelAdapter adapter;

        @BeforeEach
        void setUp() {
            adapter = new OllamaLanguageModelAdapter(mock(RestClient.class), objectMapper, MODEL);
        }

        @Test
        @DisplayName("should throw AiAnalysisException on malformed JSON")
        void shouldThrowOnMalformedJson() {
            String malformedJson = "{ this is not valid json }";

            assertThatThrownBy(() -> adapter.parseAnalysis(malformedJson, "my-pod"))
                    .isInstanceOf(AiAnalysisException.class)
                    .hasMessageContaining("Failed to parse Ollama response")
                    .hasMessageContaining("my-pod");
        }

        @Test
        @DisplayName("should throw AiAnalysisException on empty string response")
        void shouldThrowOnEmptyResponse() {
            assertThatThrownBy(() -> adapter.parseAnalysis("", "my-pod"))
                    .isInstanceOf(AiAnalysisException.class);
        }

        @Test
        @DisplayName("should throw AiAnalysisException when Ollama returns null body")
        void shouldThrowOnNullOllamaBody() {
            OllamaLanguageModelAdapter svc = adapterWithMockedResponse(null);

            assertThatThrownBy(() -> svc.analyze(FAILED_POD_EVENT, EMPTY_HISTORY))
                    .isInstanceOf(AiAnalysisException.class)
                    .hasMessageContaining("null or empty response")
                    .hasMessageContaining("payment-service-7d9f8b-xkp2q");
        }

        @Test
        @DisplayName("should throw AiAnalysisException when Ollama response field is null")
        void shouldThrowOnNullResponseField() {
            OllamaLanguageModelAdapter svc = adapterWithMockedResponse(new OllamaResponse(null));

            assertThatThrownBy(() -> svc.analyze(FAILED_POD_EVENT, EMPTY_HISTORY))
                    .isInstanceOf(AiAnalysisException.class)
                    .hasMessageContaining("null or empty response");
        }
    }

    @Nested
    @DisplayName("analyze() — happy path")
    class AnalyseHappyPathTests {

        @Test
        @DisplayName("should return AiAnalysis with all fields populated on valid Ollama response")
        void shouldReturnAnalysisOnValidResponse() {
            OllamaLanguageModelAdapter svc = adapterWithMockedResponse(new OllamaResponse(VALID_JSON_RESPONSE));

            AiAnalysis result = svc.analyze(FAILED_POD_EVENT, EMPTY_HISTORY);

            assertThat(result).isNotNull();
            assertThat(result.podName()).isEqualTo("payment-service-7d9f8b-xkp2q");
            assertThat(result.namespace()).isEqualTo("production");
            assertThat(result.verdict()).isEqualTo("CRITICAL_FAILURE");
            assertThat(result.rootCauseAnalysis()).isNotBlank();
            assertThat(result.recommendedActions()).isNotEmpty();
        }

        @Test
        @DisplayName("should handle fenced response in full analyze() flow")
        void shouldHandleFencedResponseInFullFlow() {
            String fencedResponse = "```json\n" + VALID_JSON_RESPONSE.strip() + "\n```";
            OllamaLanguageModelAdapter svc = adapterWithMockedResponse(new OllamaResponse(fencedResponse));

            AiAnalysis result = svc.analyze(FAILED_POD_EVENT, EMPTY_HISTORY);

            assertThat(result.verdict()).isEqualTo("CRITICAL_FAILURE");
        }

        @Test
        @DisplayName("should correctly map recommendedActions list from Ollama response")
        void shouldMapRecommendedActionsList() {
            OllamaLanguageModelAdapter svc = adapterWithMockedResponse(new OllamaResponse(VALID_JSON_RESPONSE));

            AiAnalysis result = svc.analyze(FAILED_POD_EVENT, EMPTY_HISTORY);

            assertThat(result.recommendedActions())
                    .hasSize(3)
                    .contains("Increase memory limits in the Pod spec")
                    .contains("Check application for memory leaks");
        }
    }
}
