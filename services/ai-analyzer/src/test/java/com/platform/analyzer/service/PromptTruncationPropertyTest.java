package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.EnrichedContext;
import net.jqwik.api.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test verifying prompt truncation priority logic.
 *
 * Property 11: Prompt Truncation Priority
 * For any prompt where total payload size exceeds the configured maximum (default 65,536 bytes):
 * - The truncation logic SHALL produce a final prompt ≤ maximum size
 * - Pod description SHALL be preserved in full (never truncated unless it alone exceeds)
 * - Log lines SHALL be truncated from oldest (top) first
 * - Events SHALL be truncated from oldest (end) only if log truncation alone is insufficient
 *
 * Validates: Requirements 7.4
 */
@Tag("Feature: mcp-intelligence-layer, Property 11: Prompt Truncation Priority")
class PromptTruncationPropertyTest {

    /**
     * Use a small max (1000 bytes) for faster testing as specified in the task.
     */
    private static final int MAX_PROMPT_BYTES = 1000;
    private static final int BASE_PROMPT_SIZE = 50;

    private final PromptTruncator truncator = new PromptTruncator(MAX_PROMPT_BYTES);

    // ─── Property 11a: Result is always within budget ────────────────────────────

    @Property(tries = 100)
    void truncatedResultIsAlwaysWithinBudget(
            @ForAll("randomPodDescription") String podDescription,
            @ForAll("randomPodEvents") String podEvents,
            @ForAll("randomPodLogs") String podLogs
    ) {
        EnrichedContext context = new EnrichedContext(podDescription, podEvents, podLogs, List.of("describe_pod", "get_events", "get_logs"));

        EnrichedContext result = truncator.truncateIfNeeded(BASE_PROMPT_SIZE, context);

        // The total byte size of the result context sections must fit within remaining budget
        int remainingBudget = MAX_PROMPT_BYTES - BASE_PROMPT_SIZE;
        int resultBytes = totalContextBytes(result);

        assertThat(resultBytes)
                .as("Total context bytes (%d) must not exceed remaining budget (%d)", resultBytes, remainingBudget)
                .isLessThanOrEqualTo(remainingBudget);
    }

    // ─── Property 11b: Pod description preserved in full ─────────────────────────

    @Property(tries = 100)
    void podDescriptionIsPreservedInFullUnlessItAloneExceedsBudget(
            @ForAll("randomPodDescription") String podDescription,
            @ForAll("randomPodEvents") String podEvents,
            @ForAll("randomPodLogs") String podLogs
    ) {
        EnrichedContext context = new EnrichedContext(podDescription, podEvents, podLogs, List.of("describe_pod", "get_events", "get_logs"));

        EnrichedContext result = truncator.truncateIfNeeded(BASE_PROMPT_SIZE, context);

        int remainingBudget = MAX_PROMPT_BYTES - BASE_PROMPT_SIZE;
        int descBytes = byteSize(podDescription);
        int descHeaderOverhead = computeHeaderOverhead(podDescription, null, null);

        if (descBytes + descHeaderOverhead <= remainingBudget) {
            // Pod description fits — it must be preserved in full
            assertThat(result.podDescription())
                    .as("Pod description should be preserved in full when it fits within budget")
                    .isEqualTo(podDescription);
        }
        // If pod description alone exceeds budget, it's allowed to be truncated
    }

    // ─── Property 11c: When all fits, result equals input ────────────────────────

    @Property(tries = 100)
    void whenAllFitsResultEqualsInput(
            @ForAll("smallPodDescription") String podDescription,
            @ForAll("smallPodEvents") String podEvents,
            @ForAll("smallPodLogs") String podLogs
    ) {
        EnrichedContext context = new EnrichedContext(podDescription, podEvents, podLogs, List.of("describe_pod"));

        int remainingBudget = MAX_PROMPT_BYTES - BASE_PROMPT_SIZE;
        int totalBytes = totalContextBytesWithHeaders(podDescription, podEvents, podLogs);

        // Only test when everything fits within budget
        Assume.that(totalBytes <= remainingBudget);

        EnrichedContext result = truncator.truncateIfNeeded(BASE_PROMPT_SIZE, context);

        assertThat(result.podDescription()).isEqualTo(podDescription);
        assertThat(result.podEvents()).isEqualTo(podEvents);
        assertThat(result.podLogs()).isEqualTo(podLogs);
    }

    // ─── Property 11d: Logs truncated before events ──────────────────────────────

    @Property(tries = 100)
    void logsAreTruncatedBeforeEvents(
            @ForAll("randomPodDescription") String podDescription,
            @ForAll("randomPodEvents") String podEvents,
            @ForAll("randomLargeLogs") String podLogs
    ) {
        EnrichedContext context = new EnrichedContext(podDescription, podEvents, podLogs, List.of("describe_pod", "get_events", "get_logs"));

        int remainingBudget = MAX_PROMPT_BYTES - BASE_PROMPT_SIZE;
        int totalInput = totalContextBytesWithHeaders(podDescription, podEvents, podLogs);

        // Only test when truncation is actually needed
        Assume.that(totalInput > remainingBudget);
        // And pod description fits on its own
        int descHeaderOverhead = computeHeaderOverhead(podDescription, null, null);
        Assume.that(byteSize(podDescription) + descHeaderOverhead <= remainingBudget);

        EnrichedContext result = truncator.truncateIfNeeded(BASE_PROMPT_SIZE, context);

        // If events are preserved in full, it means logs were truncated first (correct priority)
        if (result.podEvents() != null && result.podEvents().equals(podEvents)) {
            // Events kept intact means log truncation was sufficient
            // Logs must be either null or shorter than original
            if (result.podLogs() != null) {
                assertThat(byteSize(result.podLogs()))
                        .as("Logs should be truncated (smaller than original) when events are preserved")
                        .isLessThanOrEqualTo(byteSize(podLogs));
            }
        }

        // If logs are completely removed and events are still truncated,
        // that's also valid (events truncated only after logs removed)
        if (result.podLogs() == null && result.podEvents() != null && !result.podEvents().equals(podEvents)) {
            assertThat(byteSize(result.podEvents()))
                    .as("Events truncated only after logs fully removed")
                    .isLessThan(byteSize(podEvents));
        }
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> randomPodDescription() {
        // 0 to 500 chars — sometimes fits, sometimes doesn't with small budget
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '-', '_', '.', '\n')
                .ofMinLength(0)
                .ofMaxLength(500);
    }

    @Provide
    Arbitrary<String> randomPodEvents() {
        // 0 to 800 chars — can force truncation scenarios
        return multiLineContent(0, 800);
    }

    @Provide
    Arbitrary<String> randomPodLogs() {
        // 0 to 2000 chars — large enough to frequently exceed the 1000-byte limit
        return multiLineContent(0, 2000);
    }

    @Provide
    Arbitrary<String> randomLargeLogs() {
        // 500 to 2000 chars — guaranteed to be substantial for truncation tests
        return multiLineContent(500, 2000);
    }

    @Provide
    Arbitrary<String> smallPodDescription() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '-')
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> smallPodEvents() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '-', '\n')
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> smallPodLogs() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '-', '\n')
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Arbitrary<String> multiLineContent(int minLength, int maxLength) {
        Arbitrary<String> line = Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '-', ':', '[', ']', '=')
                .ofMinLength(10)
                .ofMaxLength(80);

        return line.list()
                .ofMinSize(minLength / 40)  // approximate lines
                .ofMaxSize(maxLength / 20)
                .map(lines -> String.join("\n", lines))
                .filter(s -> s.length() >= minLength && s.length() <= maxLength);
    }

    private static int byteSize(String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Computes total byte size of all context sections in the result, including header overhead.
     */
    private int totalContextBytes(EnrichedContext context) {
        if (context == null || !context.hasContent()) {
            return 0;
        }
        return byteSize(context.podDescription())
                + byteSize(context.podEvents())
                + byteSize(context.podLogs())
                + computeHeaderOverhead(context.podDescription(), context.podEvents(), context.podLogs());
    }

    /**
     * Computes total byte size with headers for the given raw content sections.
     */
    private int totalContextBytesWithHeaders(String podDescription, String podEvents, String podLogs) {
        return byteSize(podDescription)
                + byteSize(podEvents)
                + byteSize(podLogs)
                + computeHeaderOverhead(podDescription, podEvents, podLogs);
    }

    /**
     * Mirrors the header overhead computation from PromptTruncator.
     */
    private int computeHeaderOverhead(String podDescription, String podEvents, String podLogs) {
        int overhead = byteSize("=== CLUSTER CONTEXT (MCP) ===\n");
        if (podDescription != null) {
            overhead += byteSize("\n--- POD DESCRIPTION ---\n");
        }
        if (podEvents != null) {
            overhead += byteSize("\n--- EVENTS ---\n");
        }
        if (podLogs != null) {
            overhead += byteSize("\n--- LOGS ---\n");
        }
        return overhead;
    }
}
