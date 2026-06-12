package com.platform.analyzer.infrastructure.client.mcp;

import com.platform.analyzer.domain.model.EnrichedContext;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

/**
 * Property-based test: MCP Response Aggregation with Partial Failures (Property 8).
 *
 * For any combination of success/failure outcomes across the three MCP tool invocations
 * (describe_pod, get_events, get_logs), the resulting EnrichedContext SHALL contain non-null
 * values only for the tools that succeeded and null values for the tools that failed.
 * When all three fail, EnrichedContext.EMPTY SHALL be returned.
 * The toolsUsed list SHALL contain exactly the names of tools that returned successful responses.
 *
 * Validates: Requirements 5.4, 5.7, 5.8
 */
@Tag("Feature: mcp-intelligence-layer, Property 8: MCP Response Aggregation")
class McpAggregationPropertyTest {

    private static final String TOOL_DESCRIBE_POD = "describe_pod";
    private static final String TOOL_GET_EVENTS = "get_events";
    private static final String TOOL_GET_LOGS = "get_logs";

    private static final String BASE_URL = "http://mcp-server:3001";

    private static final String SUCCESS_JSON_TEMPLATE =
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"%s\"}]},\"error\":null}";
    private static final String ERROR_JSON =
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":null,\"error\":{\"code\":-32001,\"message\":\"Tool invocation failed\"}}";

    // ─── Generators ──────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> podNames() {
        return Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(253);
    }

    @Provide
    Arbitrary<String> namespaces() {
        return Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(63);
    }

    @Provide
    Arbitrary<boolean[]> toolOutcomes() {
        // Generate all 8 combinations of success/failure for 3 tools
        return Arbitraries.of(
                new boolean[]{false, false, false},
                new boolean[]{true, false, false},
                new boolean[]{false, true, false},
                new boolean[]{false, false, true},
                new boolean[]{true, true, false},
                new boolean[]{true, false, true},
                new boolean[]{false, true, true},
                new boolean[]{true, true, true}
        );
    }

    // ─── Property 8: MCP Response Aggregation ────────────────────────────────────

    /**
     * Property 8: MCP Response Aggregation with Partial Failures
     *
     * Validates: Requirements 5.4, 5.7, 5.8
     */
    @Property(tries = 100)
    void aggregationReflectsToolSuccessFailureCombinations(
            @ForAll("podNames") String podName,
            @ForAll("namespaces") String namespace,
            @ForAll("toolOutcomes") boolean[] outcomes) {

        boolean describePodSucceeds = outcomes[0];
        boolean getEventsSucceeds = outcomes[1];
        boolean getLogsSucceeds = outcomes[2];

        // Build RestClient with MockRestServiceServer
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // Configure 3 sequential responses: describe_pod, get_events, get_logs
        configureResponse(server, describePodSucceeds, "pod-description-content");
        configureResponse(server, getEventsSucceeds, "events-content");
        configureResponse(server, getLogsSucceeds, "logs-content");

        RestClient restClient = builder.build();
        McpClientAdapter adapter = new McpClientAdapter(restClient);

        // Act
        EnrichedContext result = adapter.retrieveContext(podName, namespace);

        // Assert: non-null only for succeeded tools
        if (describePodSucceeds) {
            assertThat(result.podDescription()).isNotNull();
        } else {
            assertThat(result.podDescription()).isNull();
        }

        if (getEventsSucceeds) {
            assertThat(result.podEvents()).isNotNull();
        } else {
            assertThat(result.podEvents()).isNull();
        }

        if (getLogsSucceeds) {
            assertThat(result.podLogs()).isNotNull();
        } else {
            assertThat(result.podLogs()).isNull();
        }

        // Assert: toolsUsed contains exactly successful tool names
        List<String> expectedToolsUsed = new ArrayList<>();
        if (describePodSucceeds) expectedToolsUsed.add(TOOL_DESCRIBE_POD);
        if (getEventsSucceeds) expectedToolsUsed.add(TOOL_GET_EVENTS);
        if (getLogsSucceeds) expectedToolsUsed.add(TOOL_GET_LOGS);

        assertThat(result.toolsUsed()).containsExactlyElementsOf(expectedToolsUsed);

        // Assert: all-fail returns EMPTY
        if (!describePodSucceeds && !getEventsSucceeds && !getLogsSucceeds) {
            assertThat(result).isEqualTo(EnrichedContext.EMPTY);
            assertThat(result.toolsUsed()).isEmpty();
            assertThat(result.hasContent()).isFalse();
        } else {
            assertThat(result.hasContent()).isTrue();
        }

        server.verify();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private void configureResponse(MockRestServiceServer server, boolean success, String textContent) {
        String json = success
                ? String.format(SUCCESS_JSON_TEMPLATE, textContent)
                : ERROR_JSON;

        server.expect(times(1), requestTo(BASE_URL + "/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));
    }
}
