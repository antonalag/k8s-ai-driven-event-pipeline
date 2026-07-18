package com.platform.analyzer.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.config.ByokProperties;
import com.platform.analyzer.infrastructure.client.byok.*;
import com.platform.analyzer.infrastructure.client.ollama.OllamaLanguageModelAdapter;
import com.platform.analyzer.infrastructure.client.ollama.OllamaRequest;
import com.platform.analyzer.infrastructure.client.ollama.OllamaResponse;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for modelUsed field propagation and backward compatibility.
 *
 * Property 12: modelUsed Propagation from Provider Config
 * For any non-blank model string M, constructing an AiAnalysis with the 8-arg constructor
 * passing M as modelUsed should yield aiAnalysis.modelUsed() == M.
 * Validates: Requirements 4.2, 4.3
 *
 * Property 13: modelUsed Backward Compatibility Default
 * For AiAnalysis constructed via legacy constructors (5-arg or 7-arg), or with null/blank
 * modelUsed, the exposed modelUsed value shall be "unknown".
 * Validates: Requirements 4.1, 4.5, 4.6, 4.7
 */
class AiAnalysisModelUsedProperties {

    // ─── Property 12: modelUsed Propagation from Provider Config ─────────────────

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 12: modelUsed propagation from provider config")
    void explicitModelUsedIsPreservedExactly(@ForAll @NotBlank String modelName) {
        AiAnalysis analysis = new AiAnalysis(
                "pod-1", "default", "CRITICAL_FAILURE", "root cause",
                List.of("action1"), List.of(), false, modelName);

        assertThat(analysis.modelUsed()).isEqualTo(modelName);
    }

    // ─── Property 12: Ollama Adapter modelUsed Propagation ─────────────────────

    /**
     * Validates: Requirements 4.2, 4.3
     *
     * For any non-blank model string configured in the Ollama adapter,
     * the resulting AiAnalysis.modelUsed() must equal that model string.
     */
    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 12: Ollama adapter propagates configured model")
    void ollamaAdapterPropagatesConfiguredModel(@ForAll @NotBlank String modelName) {
        String validJson = """
                {"podName":"test-pod","namespace":"default","verdict":"CRITICAL_FAILURE","rootCauseAnalysis":"OOMKilled","recommendedActions":["Increase memory"]}
                """;

        OllamaLanguageModelAdapter adapter = new OllamaLanguageModelAdapter(
                null, new ObjectMapper(), modelName, "http://localhost:11434") {
            @Override
            protected OllamaResponse callOllama(OllamaRequest request) {
                return new OllamaResponse(validJson);
            }
        };

        KubernetesEvent event = new KubernetesEvent(
                "test-pod", "default", PodPhase.Failed, Instant.now());

        AiAnalysis result = adapter.analyze(event, List.of(), EnrichedContext.EMPTY);

        assertThat(result.modelUsed()).isEqualTo(modelName);
    }

    /**
     * Validates: Requirements 4.2, 4.3
     *
     * For any non-blank model string configured in ByokProperties,
     * the resulting AiAnalysis.modelUsed() must equal that model string.
     */
    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 12: BYOK adapter propagates configured model")
    void byokAdapterPropagatesConfiguredModel(@ForAll @NotBlank String modelName) {
        String validJson = """
                {"podName":"test-pod","namespace":"default","verdict":"CRITICAL_FAILURE","rootCauseAnalysis":"OOMKilled","recommendedActions":["Increase memory"]}
                """;

        ObjectMapper objectMapper = new ObjectMapper();
        ByokProperties properties = new ByokProperties(
                "http://localhost:8080", "test-api-key", modelName, ProviderType.CUSTOM);
        ByokPayloadMapper payloadMapper = new ByokPayloadMapper();
        ByokResponseExtractor responseExtractor = new ByokResponseExtractor(objectMapper);

        // Wrap validJson as a JSON string value in the CUSTOM provider response format
        String escapedJson = validJson.strip().replace("\"", "\\\"");
        String providerResponse = "{\"response\":\"" + escapedJson + "\"}";

        ByokLanguageModelAdapter adapter = new ByokLanguageModelAdapter(
                null, objectMapper, payloadMapper, responseExtractor, properties) {
            @Override
            protected String callProvider(Object requestBody) {
                return providerResponse;
            }
        };

        KubernetesEvent event = new KubernetesEvent(
                "test-pod", "default", PodPhase.Failed, Instant.now());

        AiAnalysis result = adapter.analyze(event, List.of(), EnrichedContext.EMPTY);

        assertThat(result.modelUsed()).isEqualTo(modelName);
    }

    // ─── Property 13: modelUsed Backward Compatibility Default ───────────────────

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 13: modelUsed backward compatibility — 5-arg constructor")
    void fiveArgConstructorDefaultsModelUsedToUnknown(
            @ForAll @NotBlank String podName,
            @ForAll @NotBlank String verdict) {
        AiAnalysis analysis = new AiAnalysis(podName, "ns", verdict, "root", List.of());

        assertThat(analysis.modelUsed()).isEqualTo("unknown");
    }

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 13: modelUsed backward compatibility — 7-arg constructor")
    void sevenArgConstructorDefaultsModelUsedToUnknown(
            @ForAll @NotBlank String podName,
            @ForAll @NotBlank String verdict) {
        AiAnalysis analysis = new AiAnalysis(podName, "ns", verdict, "root", List.of(), List.of(), false);

        assertThat(analysis.modelUsed()).isEqualTo("unknown");
    }

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 13: modelUsed backward compatibility — null/blank defaults to unknown")
    void nullOrBlankModelUsedDefaultsToUnknown(@ForAll("nullOrBlank") String modelUsed) {
        AiAnalysis analysis = new AiAnalysis(
                "pod", "ns", "verdict", "root", List.of(), List.of(), false, modelUsed);

        assertThat(analysis.modelUsed()).isEqualTo("unknown");
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> nullOrBlank() {
        return Arbitraries.of(null, "", "   ", "\t");
    }
}
