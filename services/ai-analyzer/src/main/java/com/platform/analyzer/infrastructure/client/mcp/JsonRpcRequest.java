package com.platform.analyzer.infrastructure.client.mcp;

import java.util.Map;

/**
 * Outbound JSON-RPC 2.0 request for MCP tool invocations.
 */
public record JsonRpcRequest(
        String jsonrpc,
        String method,
        JsonRpcParams params,
        String id
) {
    /**
     * Factory method for creating a tools/call request.
     */
    public static JsonRpcRequest toolCall(String toolName, Map<String, Object> arguments, String id) {
        return new JsonRpcRequest("2.0", "tools/call", new JsonRpcParams(toolName, arguments), id);
    }
}
