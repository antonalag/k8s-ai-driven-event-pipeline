package com.platform.analyzer.infrastructure.client.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test: Prompt Structure Adapts to Context Availability (Property 10).
 *
 * For any EnrichedContext with at least one non-empty subsection:
 * - The constructed prompt SHALL contain three ordered sections (event, history, MCP context)
 * - Labeled headers appear only for non-empty subsections ("--- POD DESCRIPTION ---", "--- EVENTS ---", "--- LOGS ---")
 * - The MCP context section starts with "=== CLUSTER CONTEXT (MCP) ==="
 *
 * For any null or all-empty EnrichedContext:
 * - The prompt SHALL contain only two sections (event, history)
 * - The prompt SHALL NOT contain "=== CLUSTER CONTEXT (MCP) ===" or any MCP headers
 *
 * Validates: Requirements 7.1, 7.2, 7.3
 */
@Tag("Feature: mcp-intelligence-layer, Property 10: Prompt Structure")
class EnrichedPromptPropertyTest {

    private static final String MCP_HEADER = "=== CLUSTER CONTEXT (MCP) ===";
    private static final String POD_DESCRIPTION_HEADER = "--- POD DESCRIPTION ---";
    private static final String EVENTS_HEADER = "--- EVENTS ---";
    private static final String LOGS_HEADER = "--- LOGS ---";

    private final OllamaLanguageModelAdapter adapter = new OllamaLanguageModelAdapter(
            RestClient.builder().build(),
            new ObjectMapper(),
            "test-model",
            "http://localhost:11434"
    );

    // ─── Generators ──────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> nonEmptyContent() {
        return Arbitraries.strings()
                .alpha().numeric().withChars(' ', '-', '_', '\n', '.')
                .ofMinLength(1).ofMaxLength(200);
    }

    @Provide
    Arbitrary<KubernetesEvent> events() {
        Arbitrary<String> podNames = Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(63);
        Arbitrary<String> namespaces = Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(63);
        Arbitrary<PodPhase> phases = Arbitraries.of(PodPhase.values());

        return Combinators.combine(podNames, namespaces, phases)
                .as((pod, ns, phase) -> new KubernetesEvent(pod, ns, phase, Instant.now()));
    }

    @Provide
    Arbitrary<List<AiAnalysis>> histories() {
        Arbitrary<AiAnalysis> analysis = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.of("HEALTHY", "TRANSIENT_ISSUE", "CRITICAL_FAILURE"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),
                Arbitraries.just(List.of("action1"))
        ).as(AiAnalysis::new);

        return analysis.list().ofMinSize(0).ofMaxSize(3);
    }

    @Provide
    Arbitrary<EnrichedContext> nonEmptyContexts() {
        // Generate contexts with at least one non-empty subsection
        Arbitrary<String> nullableContent = Arbitraries.strings()
                .alpha().numeric().withChars(' ', '-', '_', '\n', '.')
                .ofMinLength(1).ofMaxLength(200)
                .injectNull(0.4);

        return Combinators.combine(nullableContent, nullableContent, nullableContent)
                .as((desc, events, logs) -> new EnrichedContext(desc, events, logs, List.of("describe_pod")))
                .filter(ctx -> ctx.hasContent());
    }

    @Provide
    Arbitrary<EnrichedContext> emptyContexts() {
        // null or all-empty (all fields null)
        return Arbitraries.of(
                EnrichedContext.EMPTY,
                new EnrichedContext(null, null, null, List.of())
        );
    }

    // ─── Property 10a: Non-empty context → three sections with labeled headers ───

    /**
     * Property 10a: For any EnrichedContext with at least one non-empty subsection,
     * the prompt contains three ordered sections and headers only for non-empty subsections.
     *
     * Validates: Requirements 7.1, 7.2, 7.3
     */
    @Property(tries = 100)
    void promptWithNonEmptyContextContainsThreeSectionsWithCorrectHeaders(
            @ForAll("events") KubernetesEvent event,
            @ForAll("histories") List<AiAnalysis> history,
            @ForAll("nonEmptyContexts") EnrichedContext context) {

        String prompt = adapter.buildPrompt(event, history, context);

        // The prompt SHALL contain MCP context header
        assertThat(prompt).contains(MCP_HEADER);

        // Verify prompt has three ordered sections: event data, history, MCP context
        int eventIndex = prompt.indexOf(event.podName());
        int mcpIndex = prompt.indexOf(MCP_HEADER);
        assertThat(eventIndex).isLessThan(mcpIndex);

        // Labeled headers appear only for non-empty subsections
        if (context.podDescription() != null) {
            assertThat(prompt).contains(POD_DESCRIPTION_HEADER);
        } else {
            assertThat(prompt).doesNotContain(POD_DESCRIPTION_HEADER);
        }

        if (context.podEvents() != null) {
            assertThat(prompt).contains(EVENTS_HEADER);
        } else {
            assertThat(prompt).doesNotContain(EVENTS_HEADER);
        }

        if (context.podLogs() != null) {
            assertThat(prompt).contains(LOGS_HEADER);
        } else {
            assertThat(prompt).doesNotContain(LOGS_HEADER);
        }

        // MCP context section starts with the MCP header
        String mcpSection = prompt.substring(mcpIndex);
        assertThat(mcpSection).startsWith(MCP_HEADER);
    }

    // ─── Property 10b: Null/empty context → legacy two-section format ────────────

    /**
     * Property 10b: For null or all-empty EnrichedContext, the prompt matches
     * legacy two-section format (no MCP headers).
     *
     * Validates: Requirements 7.1, 7.2, 7.3
     */
    @Property(tries = 100)
    void promptWithEmptyContextMatchesLegacyFormat(
            @ForAll("events") KubernetesEvent event,
            @ForAll("histories") List<AiAnalysis> history,
            @ForAll("emptyContexts") EnrichedContext context) {

        String prompt = adapter.buildPrompt(event, history, context);

        // The prompt SHALL NOT contain MCP headers
        assertThat(prompt).doesNotContain(MCP_HEADER);
        assertThat(prompt).doesNotContain(POD_DESCRIPTION_HEADER);
        assertThat(prompt).doesNotContain(EVENTS_HEADER);
        assertThat(prompt).doesNotContain(LOGS_HEADER);

        // Still contains the event data and history sections
        assertThat(prompt).contains(event.podName());
        assertThat(prompt).contains(event.namespace());
    }

    // ─── Property 10c: Null context → same as empty context ──────────────────────

    /**
     * Property 10c: For null EnrichedContext, the prompt is identical to the legacy format.
     *
     * Validates: Requirements 7.1, 7.2, 7.3
     */
    @Property(tries = 100)
    void promptWithNullContextMatchesLegacyFormat(
            @ForAll("events") KubernetesEvent event,
            @ForAll("histories") List<AiAnalysis> history) {

        String promptWithNull = adapter.buildPrompt(event, history, null);
        String promptWithEmpty = adapter.buildPrompt(event, history, EnrichedContext.EMPTY);

        // Both should produce the same result
        assertThat(promptWithNull).isEqualTo(promptWithEmpty);

        // Neither should contain MCP headers
        assertThat(promptWithNull).doesNotContain(MCP_HEADER);
        assertThat(promptWithNull).doesNotContain(POD_DESCRIPTION_HEADER);
        assertThat(promptWithNull).doesNotContain(EVENTS_HEADER);
        assertThat(promptWithNull).doesNotContain(LOGS_HEADER);
    }

    // ─── Property 10d: Section ordering is correct ───────────────────────────────

    /**
     * Property 10d: Within the MCP section, headers appear in correct order
     * (POD DESCRIPTION before EVENTS before LOGS) when present.
     *
     * Validates: Requirements 7.1, 7.2, 7.3
     */
    @Property(tries = 100)
    void mcpSectionHeadersAppearInCorrectOrder(
            @ForAll("events") KubernetesEvent event,
            @ForAll("histories") List<AiAnalysis> history,
            @ForAll("nonEmptyContent") String descContent,
            @ForAll("nonEmptyContent") String eventsContent,
            @ForAll("nonEmptyContent") String logsContent) {

        EnrichedContext fullContext = new EnrichedContext(
                descContent, eventsContent, logsContent, List.of("describe_pod", "get_events", "get_logs"));

        String prompt = adapter.buildPrompt(event, history, fullContext);

        // All headers present
        assertThat(prompt).contains(MCP_HEADER);
        assertThat(prompt).contains(POD_DESCRIPTION_HEADER);
        assertThat(prompt).contains(EVENTS_HEADER);
        assertThat(prompt).contains(LOGS_HEADER);

        // Verify ordering: MCP header < POD DESCRIPTION < EVENTS < LOGS
        int mcpIdx = prompt.indexOf(MCP_HEADER);
        int descIdx = prompt.indexOf(POD_DESCRIPTION_HEADER);
        int eventsIdx = prompt.indexOf(EVENTS_HEADER);
        int logsIdx = prompt.indexOf(LOGS_HEADER);

        assertThat(mcpIdx).isLessThan(descIdx);
        assertThat(descIdx).isLessThan(eventsIdx);
        assertThat(eventsIdx).isLessThan(logsIdx);
    }
}
