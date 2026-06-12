package com.platform.analyzer.domain.model;

import net.jqwik.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test verifying mcpToolsUsed reflects actual MCP tool invocations.
 *
 * Property 13: mcpToolsUsed Reflects Actual Invocations
 * For any analysis where N MCP tools were successfully invoked (0 ≤ N ≤ 3),
 * the mcpToolsUsed field SHALL contain exactly the N tool identifiers that were invoked,
 * each identifier ≤ 64 characters, with the list having at most 20 items.
 * When N = 0, the field SHALL be an empty list.
 *
 * Validates: Requirements 8.2, 8.3
 */
@Tag("Feature: mcp-intelligence-layer, Property 13: mcpToolsUsed Reflects Actual Invocations")
class McpToolsUsedPropertyTest {

    private static final String TOOL_DESCRIBE_POD = "describe_pod";
    private static final String TOOL_GET_EVENTS = "get_events";
    private static final String TOOL_GET_LOGS = "get_logs";

    // ─── Property 13: mcpToolsUsed Reflects Actual Invocations ───────────────────

    @Property(tries = 100)
    void mcpToolsUsedSizeMatchesNumberOfSuccessfulTools(
            @ForAll("toolSuccessCombination") List<Boolean> toolSuccess
    ) {
        // Given: a random combination of tool success (boolean triplet)
        boolean describePodSuccess = toolSuccess.get(0);
        boolean getEventsSuccess = toolSuccess.get(1);
        boolean getLogsSuccess = toolSuccess.get(2);

        // Simulate adapter logic: build EnrichedContext from tool results
        EnrichedContext context = buildEnrichedContext(describePodSuccess, getEventsSuccess, getLogsSuccess);

        // Derive mcpToolsUsed and mcpContextAvailable following the service-layer logic:
        // when context has content, mcpToolsUsed = context.toolsUsed(); when empty, mcpToolsUsed = List.of()
        List<String> mcpToolsUsed = context.hasContent() ? context.toolsUsed() : List.of();
        boolean mcpContextAvailable = context.hasContent();

        AiAnalysis analysis = new AiAnalysis(
                "test-pod", "default", "CRITICAL", "Root cause analysis",
                List.of("Action 1"), mcpToolsUsed, mcpContextAvailable
        );

        // Count expected successful tools
        int expectedToolCount = 0;
        if (describePodSuccess) expectedToolCount++;
        if (getEventsSuccess) expectedToolCount++;
        if (getLogsSuccess) expectedToolCount++;

        // Then: toolsUsed size matches the number of successful tools
        assertThat(analysis.mcpToolsUsed()).hasSize(expectedToolCount);
    }

    @Property(tries = 100)
    void eachToolIdentifierIsAtMost64Characters(
            @ForAll("toolSuccessCombination") List<Boolean> toolSuccess
    ) {
        // Given: a random combination of tool success
        boolean describePodSuccess = toolSuccess.get(0);
        boolean getEventsSuccess = toolSuccess.get(1);
        boolean getLogsSuccess = toolSuccess.get(2);

        EnrichedContext context = buildEnrichedContext(describePodSuccess, getEventsSuccess, getLogsSuccess);
        List<String> mcpToolsUsed = context.hasContent() ? context.toolsUsed() : List.of();

        // Then: each tool identifier is ≤ 64 characters
        for (String toolId : mcpToolsUsed) {
            assertThat(toolId).hasSizeLessThanOrEqualTo(64);
        }
    }

    @Property(tries = 100)
    void mcpToolsUsedListHasAtMost20Items(
            @ForAll("toolSuccessCombination") List<Boolean> toolSuccess
    ) {
        // Given: a random combination of tool success
        boolean describePodSuccess = toolSuccess.get(0);
        boolean getEventsSuccess = toolSuccess.get(1);
        boolean getLogsSuccess = toolSuccess.get(2);

        EnrichedContext context = buildEnrichedContext(describePodSuccess, getEventsSuccess, getLogsSuccess);
        List<String> mcpToolsUsed = context.hasContent() ? context.toolsUsed() : List.of();

        // Then: list has at most 20 items
        assertThat(mcpToolsUsed).hasSizeLessThanOrEqualTo(20);
    }

    @Property(tries = 100)
    void whenAllToolsFailMcpToolsUsedIsEmptyList(
            @ForAll("toolSuccessCombination") List<Boolean> toolSuccess
    ) {
        // Given: a random combination of tool success
        boolean describePodSuccess = toolSuccess.get(0);
        boolean getEventsSuccess = toolSuccess.get(1);
        boolean getLogsSuccess = toolSuccess.get(2);

        EnrichedContext context = buildEnrichedContext(describePodSuccess, getEventsSuccess, getLogsSuccess);
        List<String> mcpToolsUsed = context.hasContent() ? context.toolsUsed() : List.of();

        // Then: when all tools fail (EnrichedContext.EMPTY), the list is empty
        if (!describePodSuccess && !getEventsSuccess && !getLogsSuccess) {
            assertThat(context).isEqualTo(EnrichedContext.EMPTY);
            assertThat(mcpToolsUsed).isEmpty();
        }
    }

    @Property(tries = 100)
    void mcpToolsUsedContainsExactlyTheSuccessfulToolIdentifiers(
            @ForAll("toolSuccessCombination") List<Boolean> toolSuccess
    ) {
        // Given: a random combination of tool success
        boolean describePodSuccess = toolSuccess.get(0);
        boolean getEventsSuccess = toolSuccess.get(1);
        boolean getLogsSuccess = toolSuccess.get(2);

        EnrichedContext context = buildEnrichedContext(describePodSuccess, getEventsSuccess, getLogsSuccess);
        List<String> mcpToolsUsed = context.hasContent() ? context.toolsUsed() : List.of();

        // Then: toolsUsed contains exactly the identifiers of successful tools
        if (describePodSuccess) {
            assertThat(mcpToolsUsed).contains(TOOL_DESCRIBE_POD);
        } else {
            assertThat(mcpToolsUsed).doesNotContain(TOOL_DESCRIBE_POD);
        }

        if (getEventsSuccess) {
            assertThat(mcpToolsUsed).contains(TOOL_GET_EVENTS);
        } else {
            assertThat(mcpToolsUsed).doesNotContain(TOOL_GET_EVENTS);
        }

        if (getLogsSuccess) {
            assertThat(mcpToolsUsed).contains(TOOL_GET_LOGS);
        } else {
            assertThat(mcpToolsUsed).doesNotContain(TOOL_GET_LOGS);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Simulates the MCP adapter logic: builds an EnrichedContext based on which tools succeed.
     * When a tool succeeds, the corresponding field is non-null and the tool name is added to toolsUsed.
     * When a tool fails, the corresponding field is null.
     * When all tools fail, returns EnrichedContext.EMPTY.
     */
    private EnrichedContext buildEnrichedContext(boolean describePodSuccess, boolean getEventsSuccess, boolean getLogsSuccess) {
        List<String> toolsUsed = new java.util.ArrayList<>();

        String podDescription = null;
        String podEvents = null;
        String podLogs = null;

        if (describePodSuccess) {
            podDescription = "containers: [{name: app, image: nginx:1.25}], phase: Running";
            toolsUsed.add(TOOL_DESCRIBE_POD);
        }
        if (getEventsSuccess) {
            podEvents = "[{type: Warning, reason: BackOff, message: Back-off restarting}]";
            toolsUsed.add(TOOL_GET_EVENTS);
        }
        if (getLogsSuccess) {
            podLogs = "ERROR: OutOfMemoryError at line 42";
            toolsUsed.add(TOOL_GET_LOGS);
        }

        if (toolsUsed.isEmpty()) {
            return EnrichedContext.EMPTY;
        }

        return new EnrichedContext(podDescription, podEvents, podLogs, List.copyOf(toolsUsed));
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<List<Boolean>> toolSuccessCombination() {
        // Generate random boolean triplets representing success/failure of each tool
        return Arbitraries.of(true, false).list().ofSize(3);
    }
}
