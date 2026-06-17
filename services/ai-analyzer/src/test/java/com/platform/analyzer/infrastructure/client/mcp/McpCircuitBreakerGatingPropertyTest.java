package com.platform.analyzer.infrastructure.client.mcp;

import com.platform.analyzer.config.ResilientMcpContextAdapter;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.ports.McpContextPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import net.jqwik.api.*;
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
 * Property-based tests for MCP circuit breaker gating behavior.
 *
 * Property 1: Circuit breaker state determines MCP invocation and result fields
 * Property 2: Sequential MCP tool invocation order
 * Property 3: Partial success produces correct EnrichedContext
 *
 * Validates: Requirements 5.1, 5.2, 5.4, 5.5, 5.6, 6.2, 6.3
 */
@Tag("Feature: e2e-validation-diagnostic-calibration, Property 1: Circuit breaker state determines MCP invocation and result fields")
@Tag("Feature: e2e-validation-diagnostic-calibration, Property 2: Sequential MCP tool invocation order")
@Tag("Feature: e2e-validation-diagnostic-calibration, Property 3: Partial success produces correct EnrichedContext")
class McpCircuitBreakerGatingPropertyTest {

    private static final String BASE_URL = "http://mcp-server:3001";

    private static final String TOOL_DESCRIBE_POD = "describe_pod";
    private static final String TOOL_GET_EVENTS = "get_events";
    private static final String TOOL_GET_LOGS = "get_logs";

    private static final String SUCCESS_JSON_TEMPLATE =
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"%s\"}]},\"error\":null}";
    private static final String ERROR_JSON =
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":null,\"error\":{\"code\":-32001,\"message\":\"Tool invocation failed\"}}";

    // ─── Generators ──────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> podNames() {
        return Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(63);
    }

    @Provide
    Arbitrary<String> namespaces() {
        return Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(1).ofMaxLength(63);
    }

    @Provide
    Arbitrary<boolean[]> toolOutcomes() {
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

    @Provide
    Arbitrary<String> closedOrHalfOpenState() {
        return Arbitraries.of("CLOSED", "HALF_OPEN");
    }

    // ─── Property 1: Circuit breaker state determines MCP invocation and result fields ───

    /**
     * Property 1a: When circuit breaker is OPEN, ResilientMcpContextAdapter returns
     * EnrichedContext.EMPTY with empty toolsUsed and mcpContextAvailable = false.
     *
     * Validates: Requirements 5.1, 5.5
     */
    @Property(tries = 100)
    void openCircuitBreakerReturnsEmptyContextWithoutMcpInvocation(
            @ForAll("podNames") String podName,
            @ForAll("namespaces") String namespace) {

        // Arrange: create a circuit breaker forced into OPEN state
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(1)
                .failureRateThreshold(1)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(config)
                .circuitBreaker("mcp-open-test-" + System.nanoTime());
        circuitBreaker.transitionToOpenState();

        // Use a delegate that should NEVER be called
        McpContextPort neverCalledDelegate = (p, n) -> {
            throw new AssertionError("Delegate should not be invoked when circuit breaker is OPEN");
        };

        ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(neverCalledDelegate, circuitBreaker);

        // Act
        EnrichedContext result = adapter.retrieveContext(podName, namespace);

        // Assert: EnrichedContext.EMPTY returned
        assertThat(result).isEqualTo(EnrichedContext.EMPTY);
        assertThat(result.podDescription()).isNull();
        assertThat(result.podEvents()).isNull();
        assertThat(result.podLogs()).isNull();
        assertThat(result.toolsUsed()).isEmpty();
        assertThat(result.hasContent()).isFalse();
    }

    /**
     * Property 1b: When circuit breaker is CLOSED or HALF_OPEN, MCP tools are invoked
     * (delegate is called) and can produce non-empty results.
     *
     * Validates: Requirements 5.1, 5.5
     */
    @Property(tries = 100)
    void closedOrHalfOpenCircuitBreakerInvokesMcpTools(
            @ForAll("podNames") String podName,
            @ForAll("namespaces") String namespace,
            @ForAll("closedOrHalfOpenState") String state) {

        // Arrange: circuit breaker in CLOSED or HALF_OPEN state
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(config)
                .circuitBreaker("mcp-active-test-" + System.nanoTime());

        if ("HALF_OPEN".equals(state)) {
            circuitBreaker.transitionToOpenState();
            circuitBreaker.transitionToHalfOpenState();
        }
        // CLOSED is the default state

        // Build MockRestServiceServer for testing the McpClientAdapter delegate
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // All 3 tools succeed
        configureSuccessResponse(server, "pod-description-data");
        configureSuccessResponse(server, "events-data");
        configureSuccessResponse(server, "logs-data");

        RestClient restClient = builder.build();
        McpClientAdapter delegate = new McpClientAdapter(restClient);

        ResilientMcpContextAdapter adapter = new ResilientMcpContextAdapter(delegate, circuitBreaker);

        // Act
        EnrichedContext result = adapter.retrieveContext(podName, namespace);

        // Assert: MCP tools were invoked (delegate was called) and returned content
        assertThat(result.podDescription()).isNotNull();
        assertThat(result.podEvents()).isNotNull();
        assertThat(result.podLogs()).isNotNull();
        assertThat(result.toolsUsed()).containsExactly(TOOL_DESCRIBE_POD, TOOL_GET_EVENTS, TOOL_GET_LOGS);
        assertThat(result.hasContent()).isTrue();

        server.verify();
    }

    // ─── Property 2: Sequential MCP tool invocation order ────────────────────────

    /**
     * Property 2: Tools invoked in exact sequence describe_pod → get_events → get_logs,
     * regardless of individual tool success/failure.
     *
     * The MockRestServiceServer enforces strict ordering — responses are consumed sequentially.
     * If invocation order differed from expectations, the test would fail because the
     * mocked responses are position-dependent.
     *
     * Validates: Requirements 5.2
     */
    @Property(tries = 100)
    void mcpToolsInvokedInSequentialOrder(
            @ForAll("podNames") String podName,
            @ForAll("namespaces") String namespace,
            @ForAll("toolOutcomes") boolean[] outcomes) {

        boolean describePodSucceeds = outcomes[0];
        boolean getEventsSucceeds = outcomes[1];
        boolean getLogsSucceeds = outcomes[2];

        // Build RestClient with MockRestServiceServer
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // Configure 3 responses in strict sequential order: describe_pod → get_events → get_logs
        configureResponseForTool(server, describePodSucceeds, "describe-pod-content");
        configureResponseForTool(server, getEventsSucceeds, "get-events-content");
        configureResponseForTool(server, getLogsSucceeds, "get-logs-content");

        RestClient restClient = builder.build();
        McpClientAdapter adapter = new McpClientAdapter(restClient);

        // Act — adapter must call all 3 tools in the exact order
        EnrichedContext result = adapter.retrieveContext(podName, namespace);

        // Assert: all 3 requests were made in the expected order
        server.verify();

        // Assert: toolsUsed reflects only successful tools
        List<String> expectedToolsUsed = new ArrayList<>();
        if (describePodSucceeds) expectedToolsUsed.add(TOOL_DESCRIBE_POD);
        if (getEventsSucceeds) expectedToolsUsed.add(TOOL_GET_EVENTS);
        if (getLogsSucceeds) expectedToolsUsed.add(TOOL_GET_LOGS);
        assertThat(result.toolsUsed()).containsExactlyElementsOf(expectedToolsUsed);
    }

    // ─── Property 3: Partial success produces correct EnrichedContext ─────────────

    /**
     * Property 3a: For any combination of success/failure across 3 tools,
     * EnrichedContext has non-null fields only for successful tools.
     *
     * Validates: Requirements 5.4, 5.6, 6.2, 6.3
     */
    @Property(tries = 100)
    void partialSuccessProducesCorrectEnrichedContextFields(
            @ForAll("podNames") String podName,
            @ForAll("namespaces") String namespace,
            @ForAll("toolOutcomes") boolean[] outcomes) {

        boolean describePodSucceeds = outcomes[0];
        boolean getEventsSucceeds = outcomes[1];
        boolean getLogsSucceeds = outcomes[2];

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        configureResponseForTool(server, describePodSucceeds, "pod-description-content");
        configureResponseForTool(server, getEventsSucceeds, "events-content");
        configureResponseForTool(server, getLogsSucceeds, "logs-content");

        RestClient restClient = builder.build();
        McpClientAdapter adapter = new McpClientAdapter(restClient);

        // Act
        EnrichedContext result = adapter.retrieveContext(podName, namespace);

        // Assert: non-null fields only for successful tools
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

        server.verify();
    }

    /**
     * Property 3b: mcpToolsUsed contains only successful tool names
     * and mcpContextAvailable = mcpToolsUsed.size() > 0.
     *
     * Validates: Requirements 5.4, 5.6, 6.2, 6.3
     */
    @Property(tries = 100)
    void partialSuccessToolsUsedAndContextAvailableAreCorrect(
            @ForAll("podNames") String podName,
            @ForAll("namespaces") String namespace,
            @ForAll("toolOutcomes") boolean[] outcomes) {

        boolean describePodSucceeds = outcomes[0];
        boolean getEventsSucceeds = outcomes[1];
        boolean getLogsSucceeds = outcomes[2];

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        configureResponseForTool(server, describePodSucceeds, "pod-description-content");
        configureResponseForTool(server, getEventsSucceeds, "events-content");
        configureResponseForTool(server, getLogsSucceeds, "logs-content");

        RestClient restClient = builder.build();
        McpClientAdapter adapter = new McpClientAdapter(restClient);

        // Act
        EnrichedContext result = adapter.retrieveContext(podName, namespace);

        // Build expected toolsUsed list
        List<String> expectedToolsUsed = new ArrayList<>();
        if (describePodSucceeds) expectedToolsUsed.add(TOOL_DESCRIBE_POD);
        if (getEventsSucceeds) expectedToolsUsed.add(TOOL_GET_EVENTS);
        if (getLogsSucceeds) expectedToolsUsed.add(TOOL_GET_LOGS);

        // Assert: toolsUsed contains only successful tool names
        assertThat(result.toolsUsed()).containsExactlyElementsOf(expectedToolsUsed);

        // Assert: mcpContextAvailable = toolsUsed.size() > 0
        boolean mcpContextAvailable = !result.toolsUsed().isEmpty();
        assertThat(mcpContextAvailable).isEqualTo(result.hasContent());

        // Assert: when all fail, result equals EMPTY
        if (expectedToolsUsed.isEmpty()) {
            assertThat(result).isEqualTo(EnrichedContext.EMPTY);
            assertThat(result.toolsUsed()).isEmpty();
            assertThat(result.hasContent()).isFalse();
        }

        server.verify();
    }

    /**
     * Property 3c: toolsUsed is always a subset of {"describe_pod", "get_events", "get_logs"}
     * with size between 0 and 3.
     *
     * Validates: Requirements 5.4, 5.6, 6.2, 6.3
     */
    @Property(tries = 100)
    void toolsUsedIsAlwaysValidSubset(
            @ForAll("podNames") String podName,
            @ForAll("namespaces") String namespace,
            @ForAll("toolOutcomes") boolean[] outcomes) {

        boolean describePodSucceeds = outcomes[0];
        boolean getEventsSucceeds = outcomes[1];
        boolean getLogsSucceeds = outcomes[2];

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        configureResponseForTool(server, describePodSucceeds, "pod-data");
        configureResponseForTool(server, getEventsSucceeds, "events-data");
        configureResponseForTool(server, getLogsSucceeds, "logs-data");

        RestClient restClient = builder.build();
        McpClientAdapter adapter = new McpClientAdapter(restClient);

        // Act
        EnrichedContext result = adapter.retrieveContext(podName, namespace);

        // Assert: toolsUsed is a subset of known tools
        assertThat(result.toolsUsed())
                .isSubsetOf(TOOL_DESCRIBE_POD, TOOL_GET_EVENTS, TOOL_GET_LOGS);

        // Assert: size between 0 and 3
        assertThat(result.toolsUsed().size()).isBetween(0, 3);

        server.verify();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private void configureSuccessResponse(MockRestServiceServer server, String textContent) {
        String json = String.format(SUCCESS_JSON_TEMPLATE, textContent);
        server.expect(times(1), requestTo(BASE_URL + "/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void configureResponseForTool(MockRestServiceServer server, boolean success, String textContent) {
        String json = success
                ? String.format(SUCCESS_JSON_TEMPLATE, textContent)
                : ERROR_JSON;
        server.expect(times(1), requestTo(BASE_URL + "/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));
    }
}
