package com.platform.analyzer.infrastructure.client.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.config.McpProperties;
import com.platform.analyzer.domain.model.FixImageCommand;
import com.platform.analyzer.domain.model.RemediationResult;
import com.platform.analyzer.domain.model.RestartCommand;
import com.platform.analyzer.domain.model.ScaleCommand;
import com.platform.analyzer.domain.ports.RemediationException;
import com.platform.analyzer.domain.ports.RemediationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Infrastructure adapter implementing {@link RemediationPort} via JSON-RPC 2.0
 * communication with the MCP Server's write-back tools.
 *
 * <p>Not annotated with @Component — instantiated via @Bean method in MutationResilienceConfig.
 */
public class RemediationAdapter implements RemediationPort {

    private static final Logger log = LoggerFactory.getLogger(RemediationAdapter.class);

    private static final String TOOL_RESTART_DEPLOYMENT = "restart_deployment";
    private static final String TOOL_SCALE_DEPLOYMENT = "scale_deployment";
    private static final String TOOL_FIX_CONTAINER_IMAGE = "fix_container_image";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RestClient restClient;

    public RemediationAdapter(McpProperties properties) {
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
    RemediationAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public RemediationResult restartDeployment(RestartCommand command) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("deploymentName", command.deploymentName());
        arguments.put("namespace", command.namespace());
        arguments.put("correlationId", command.correlationId().toString());

        return invokeTool(TOOL_RESTART_DEPLOYMENT, arguments, command.correlationId());
    }

    @Override
    public RemediationResult scaleDeployment(ScaleCommand command) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("deploymentName", command.deploymentName());
        arguments.put("namespace", command.namespace());
        arguments.put("replicas", command.replicas());
        arguments.put("correlationId", command.correlationId().toString());

        return invokeTool(TOOL_SCALE_DEPLOYMENT, arguments, command.correlationId());
    }

    @Override
    public RemediationResult fixContainerImage(FixImageCommand command) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("deploymentName", command.deploymentName());
        arguments.put("namespace", command.namespace());
        arguments.put("containerName", command.containerName());
        arguments.put("correctImage", command.correctImage());
        arguments.put("correlationId", command.correlationId().toString());

        return invokeTool(TOOL_FIX_CONTAINER_IMAGE, arguments, command.correlationId());
    }

    /**
     * Invokes a single MCP write-back tool via JSON-RPC 2.0.
     *
     * @param toolName the tool to invoke
     * @param arguments tool parameters
     * @param correlationId for MDC logging context
     * @return RemediationResult.Success or RemediationResult.Failure
     * @throws RemediationException on infrastructure failure (connection, timeout)
     */
    private RemediationResult invokeTool(String toolName, Map<String, Object> arguments, UUID correlationId) {
        MDC.put("correlationId", correlationId.toString());
        try {
            String requestId = UUID.randomUUID().toString();
            JsonRpcRequest request = JsonRpcRequest.toolCall(toolName, arguments, requestId);

            log.debug("Dispatching remediation tool '{}' for correlationId={}", toolName, correlationId);

            JsonRpcResponse response = restClient.post()
                    .uri("/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonRpcResponse.class);

            if (response == null) {
                throw new RemediationException(
                        "MCP Server returned null response for tool '" + toolName + "' correlationId=" + correlationId);
            }

            if (response.isError()) {
                String errorMessage = response.error() != null ? response.error().message() : "unknown error";
                int errorCode = response.error() != null ? response.error().code() : -1;
                String errorCodeStr = mapErrorCode(errorCode);

                log.debug("Remediation tool '{}' returned error: code={}, message={}", toolName, errorCode, errorMessage);

                return new RemediationResult.Failure(
                        toolName,
                        errorCodeStr,
                        errorMessage,
                        Instant.now()
                );
            }

            // Parse successful result from content[0].text
            String text = extractText(response);
            if (text == null) {
                throw new RemediationException(
                        "MCP Server returned empty content for tool '" + toolName + "' correlationId=" + correlationId);
            }

            Map<String, Object> resultMap = OBJECT_MAPPER.readValue(text, MAP_TYPE);
            log.debug("Remediation tool '{}' completed successfully for correlationId={}", toolName, correlationId);

            return new RemediationResult.Success(
                    toolName,
                    Instant.now(),
                    resultMap
            );

        } catch (RemediationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RemediationException(
                    "Failed to invoke MCP tool '" + toolName + "' for correlationId=" + correlationId + ": " + ex.getMessage(),
                    ex);
        } finally {
            MDC.remove("correlationId");
        }
    }

    /**
     * Maps numeric JSON-RPC error codes to human-readable error code strings.
     */
    private String mapErrorCode(int code) {
        return switch (code) {
            case -32001 -> "RESOURCE_NOT_FOUND";
            case -32003 -> "TIMEOUT";
            case -32004 -> "UPSTREAM_FAILURE";
            case -32403 -> "FORBIDDEN";
            case -32602 -> "INVALID_PARAMS";
            default -> "MCP_ERROR_" + code;
        };
    }

    /**
     * Extracts text content from the first ContentBlock in a successful JSON-RPC response.
     */
    private String extractText(JsonRpcResponse response) {
        if (response.result() == null || response.result().content() == null || response.result().content().isEmpty()) {
            return null;
        }
        ContentBlock first = response.result().content().getFirst();
        return first != null ? first.text() : null;
    }
}
