package com.platform.analyzer.infrastructure.client.mcp;

import com.platform.analyzer.config.McpProperties;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.ports.McpContextPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Infrastructure adapter that communicates with the MCP Server via JSON-RPC 2.0 over HTTP.
 * Invokes three tools sequentially: describe_pod → get_events → get_logs.
 * Handles partial failures: if one tool fails, others can still succeed.
 *
 * <p>Not annotated with @Component — instantiated via @Bean method in McpConfig (following Ollama/BYOK pattern).
 */
public class McpClientAdapter implements McpContextPort {

    private static final Logger log = LoggerFactory.getLogger(McpClientAdapter.class);

    private static final String TOOL_DESCRIBE_POD = "describe_pod";
    private static final String TOOL_GET_EVENTS = "get_events";
    private static final String TOOL_GET_LOGS = "get_logs";

    private final RestClient restClient;

    public McpClientAdapter(McpProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectionTimeout() * 1000);
        requestFactory.setReadTimeout(properties.readTimeout() * 1000);

        this.restClient = RestClient.builder()
                .baseUrl(properties.serverUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * Constructor for testing — accepts a pre-built RestClient.
     */
    McpClientAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public EnrichedContext retrieveContext(String podName, String namespace) {
        List<String> toolsUsed = new ArrayList<>();

        Map<String, Object> commonArgs = Map.of("podName", podName, "namespace", namespace);

        String podDescription = invokeTool(TOOL_DESCRIBE_POD, commonArgs, toolsUsed);
        String podEvents = invokeTool(TOOL_GET_EVENTS, commonArgs, toolsUsed);
        String podLogs = invokeTool(TOOL_GET_LOGS, commonArgs, toolsUsed);

        if (toolsUsed.isEmpty()) {
            return EnrichedContext.EMPTY;
        }

        return new EnrichedContext(podDescription, podEvents, podLogs, List.copyOf(toolsUsed));
    }

    /**
     * Invokes a single MCP tool via JSON-RPC 2.0. On success, adds the tool name to toolsUsed
     * and returns the text content. On failure, logs a warning and returns null.
     */
    private String invokeTool(String toolName, Map<String, Object> arguments, List<String> toolsUsed) {
        try {
            JsonRpcRequest request = JsonRpcRequest.toolCall(toolName, arguments, UUID.randomUUID().toString());

            JsonRpcResponse response = restClient.post()
                    .uri("/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonRpcResponse.class);

            if (response == null || response.isError()) {
                String errorMsg = (response != null && response.error() != null)
                        ? response.error().message()
                        : "null response";
                log.warn("MCP tool '{}' returned error: {}", toolName, errorMsg);
                return null;
            }

            String text = extractText(response);
            if (text != null) {
                toolsUsed.add(toolName);
            }
            return text;

        } catch (Exception ex) {
            log.warn("MCP tool '{}' invocation failed: {}", toolName, ex.getMessage());
            return null;
        }
    }

    /**
     * Extracts the text content from the first ContentBlock in a successful response.
     */
    private String extractText(JsonRpcResponse response) {
        if (response.result() == null || response.result().content() == null || response.result().content().isEmpty()) {
            return null;
        }
        ContentBlock first = response.result().content().getFirst();
        return first != null ? first.text() : null;
    }
}
