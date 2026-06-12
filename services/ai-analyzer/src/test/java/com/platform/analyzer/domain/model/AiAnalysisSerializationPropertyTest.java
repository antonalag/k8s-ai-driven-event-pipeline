package com.platform.analyzer.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test verifying backward-compatible serialization of AiAnalysis.
 *
 * Property 12: AiAnalysis Backward-Compatible Serialization
 * For any AiAnalysis record produced by the enriched pipeline, serializing it to JSON
 * and deserializing with a consumer that only knows the original five fields
 * (podName, namespace, verdict, rootCauseAnalysis, recommendedActions)
 * SHALL succeed without error or data loss for those original fields.
 *
 * Validates: Requirements 8.1
 */
@Tag("Feature: mcp-intelligence-layer, Property 12: AiAnalysis Backward-Compatible Serialization")
class AiAnalysisSerializationPropertyTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Legacy consumer record — knows only the original 5 fields.
     * Uses @JsonIgnoreProperties(ignoreUnknown = true) to tolerate new additive fields.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LegacyAiAnalysis(
            String podName,
            String namespace,
            String verdict,
            String rootCauseAnalysis,
            List<String> recommendedActions
    ) {}

    // ─── Property 12: Backward-Compatible Serialization ──────────────────────────

    @Property(tries = 100)
    void serializingEnrichedAiAnalysisAndDeserializingAsLegacyPreservesOriginalFields(
            @ForAll("randomPodName") String podName,
            @ForAll("randomNamespace") String namespace,
            @ForAll("randomVerdict") String verdict,
            @ForAll("randomRootCauseAnalysis") String rootCauseAnalysis,
            @ForAll("randomRecommendedActions") List<String> recommendedActions,
            @ForAll("randomMcpToolsUsed") List<String> mcpToolsUsed,
            @ForAll("randomMcpContextAvailable") boolean mcpContextAvailable
    ) throws Exception {
        // Given: a full AiAnalysis record with all 7 fields populated
        AiAnalysis enriched = new AiAnalysis(
                podName, namespace, verdict, rootCauseAnalysis,
                recommendedActions, mcpToolsUsed, mcpContextAvailable
        );

        // When: serialize to JSON and deserialize with legacy consumer
        String json = objectMapper.writeValueAsString(enriched);
        LegacyAiAnalysis legacy = objectMapper.readValue(json, LegacyAiAnalysis.class);

        // Then: original 5 fields are preserved exactly
        assertThat(legacy.podName()).isEqualTo(podName);
        assertThat(legacy.namespace()).isEqualTo(namespace);
        assertThat(legacy.verdict()).isEqualTo(verdict);
        assertThat(legacy.rootCauseAnalysis()).isEqualTo(rootCauseAnalysis);
        assertThat(legacy.recommendedActions()).isEqualTo(recommendedActions);
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> randomPodName() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-')
                .ofMinLength(1)
                .ofMaxLength(63);
    }

    @Provide
    Arbitrary<String> randomNamespace() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-')
                .ofMinLength(1)
                .ofMaxLength(63);
    }

    @Provide
    Arbitrary<String> randomVerdict() {
        return Arbitraries.of("HEALTHY", "TRANSIENT_ISSUE", "CRITICAL_FAILURE");
    }

    @Provide
    Arbitrary<String> randomRootCauseAnalysis() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '.', ',', '-', ':')
                .ofMinLength(5)
                .ofMaxLength(200);
    }

    @Provide
    Arbitrary<List<String>> randomRecommendedActions() {
        Arbitrary<String> action = Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '-')
                .ofMinLength(5)
                .ofMaxLength(100);
        return action.list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<String>> randomMcpToolsUsed() {
        Arbitrary<String> tool = Arbitraries.of(
                "describe_pod", "get_events", "get_logs"
        );
        return tool.list().ofMinSize(0).ofMaxSize(3).uniqueElements();
    }

    @Provide
    Arbitrary<Boolean> randomMcpContextAvailable() {
        return Arbitraries.of(true, false);
    }
}
