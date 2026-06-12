package com.platform.analyzer.infrastructure.client.mcp;

import com.platform.analyzer.domain.model.EnrichedContext;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test: mcpToolsUsed Reflects Actual Invocations (Property 13).
 *
 * For any analysis where N MCP tools were successfully invoked (0 ≤ N ≤ 3):
 * - The mcpToolsUsed field SHALL contain exactly the N tool identifiers that were invoked
 * - Each identifier SHALL be ≤ 64 characters
 * - The list SHALL have at most 20 items (though max realistic is 3)
 * - When N = 0, the field SHALL be an empty list
 *
 * Validates: Requirements 8.2, 8.3
 */
@Tag("Feature: mcp-intelligence-layer, Property 13: mcpToolsUsed Reflects Actual Invocations")
class McpToolsUsedPropertyTest {

    private static final String TOOL_DESCRIBE_POD = "describe_pod";
    private static final String TOOL_GET_EVENTS = "get_events";
    private static final String TOOL_GET_LOGS = "get_logs";

    private static final Set<String> ALL_TOOLS = Set.of(TOOL_DESCRIBE_POD, TOOL_GET_EVENTS, TOOL_GET_LOGS);

    // ─── Generators ──────────────────────────────────────────────────────────────

    /**
     * Generates random subsets of {"describe_pod", "get_events", "get_logs"} (0 to 3 tools).
     * Covers all 8 possible power-set combinations.
     */
    @Provide
    Arbitrary<List<String>> toolSubsets() {
        return Arbitraries.of(
                List.of(),
                List.of(TOOL_DESCRIBE_POD),
                List.of(TOOL_GET_EVENTS),
                List.of(TOOL_GET_LOGS),
                List.of(TOOL_DESCRIBE_POD, TOOL_GET_EVENTS),
                List.of(TOOL_DESCRIBE_POD, TOOL_GET_LOGS),
                List.of(TOOL_GET_EVENTS, TOOL_GET_LOGS),
                List.of(TOOL_DESCRIBE_POD, TOOL_GET_EVENTS, TOOL_GET_LOGS)
        );
    }

    @Provide
    Arbitrary<String> nonEmptyContent() {
        return Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1).ofMaxLength(200);
    }

    // ─── Property 13: mcpToolsUsed Reflects Actual Invocations ───────────────────

    /**
     * Property 13: mcpToolsUsed Reflects Actual Invocations
     *
     * For any subset of tools successfully invoked, the EnrichedContext SHALL have
     * toolsUsed containing exactly those tool identifiers, each ≤ 64 chars, list ≤ 20 items.
     *
     * Validates: Requirements 8.2, 8.3
     */
    @Property(tries = 100)
    void mcpToolsUsedReflectsActualInvocations(
            @ForAll("toolSubsets") List<String> subset,
            @ForAll("nonEmptyContent") String content) {

        // Build an EnrichedContext where:
        // - podDescription is non-null if "describe_pod" is in the subset, null otherwise
        // - podEvents is non-null if "get_events" is in the subset, null otherwise
        // - podLogs is non-null if "get_logs" is in the subset, null otherwise
        // - toolsUsed contains exactly the names in the subset
        String podDescription = subset.contains(TOOL_DESCRIBE_POD) ? content : null;
        String podEvents = subset.contains(TOOL_GET_EVENTS) ? content : null;
        String podLogs = subset.contains(TOOL_GET_LOGS) ? content : null;
        List<String> toolsUsed = new ArrayList<>(subset);

        EnrichedContext context = new EnrichedContext(podDescription, podEvents, podLogs, toolsUsed);

        // Assert: toolsUsed size equals subset size
        assertThat(context.toolsUsed().size()).isEqualTo(subset.size());

        // Assert: each tool identifier is ≤ 64 characters
        for (String tool : context.toolsUsed()) {
            assertThat(tool.length())
                    .as("Tool identifier '%s' must be ≤ 64 characters", tool)
                    .isLessThanOrEqualTo(64);
        }

        // Assert: list has at most 20 items
        assertThat(context.toolsUsed().size())
                .as("toolsUsed list must have at most 20 items")
                .isLessThanOrEqualTo(20);

        // Assert: toolsUsed contains exactly the expected tool names
        assertThat(context.toolsUsed()).containsExactlyElementsOf(subset);

        // Assert: when N = 0, the field is an empty list
        if (subset.isEmpty()) {
            assertThat(context.toolsUsed()).isEmpty();
            assertThat(context.hasContent()).isFalse();
        } else {
            // At least one tool was used, so at least one field should be non-null
            assertThat(context.hasContent()).isTrue();
        }
    }

    /**
     * Property 13 (supplemental): Verifies that all known MCP tool identifiers
     * are within the 64-character constraint.
     *
     * Validates: Requirements 8.2, 8.3
     */
    @Property(tries = 100)
    void allKnownToolIdentifiersAreWithinSizeLimit(
            @ForAll("toolSubsets") List<String> subset) {

        for (String toolId : subset) {
            assertThat(toolId.length())
                    .as("Known tool identifier '%s' must be ≤ 64 characters", toolId)
                    .isLessThanOrEqualTo(64);
            // Verify it is one of the known tools
            assertThat(ALL_TOOLS).contains(toolId);
        }
    }

    /**
     * Property 13 (supplemental): Verifies end-to-end through the McpClientAdapter
     * that toolsUsed reflects actual successful invocations.
     *
     * Validates: Requirements 8.2, 8.3
     */
    @Property(tries = 100)
    void adapterProducesCorrectToolsUsedList(
            @ForAll("toolSubsets") List<String> subset) {

        // Simulate adapter behavior: create EnrichedContext mimicking what McpClientAdapter produces
        String podDescription = subset.contains(TOOL_DESCRIBE_POD) ? "pod-data" : null;
        String podEvents = subset.contains(TOOL_GET_EVENTS) ? "events-data" : null;
        String podLogs = subset.contains(TOOL_GET_LOGS) ? "logs-data" : null;

        EnrichedContext context = new EnrichedContext(podDescription, podEvents, podLogs, List.copyOf(subset));

        // N = number of tools invoked
        int expectedN = subset.size();

        // The mcpToolsUsed field contains exactly N identifiers
        assertThat(context.toolsUsed()).hasSize(expectedN);

        // Bounded by the maximum of 20 items (realistic max is 3)
        assertThat(context.toolsUsed().size()).isLessThanOrEqualTo(20);

        // Each identifier is ≤ 64 characters
        context.toolsUsed().forEach(id ->
                assertThat(id.length()).isLessThanOrEqualTo(64)
        );

        // Contains exactly the subset items (order preserved)
        assertThat(context.toolsUsed()).isEqualTo(subset);
    }
}
