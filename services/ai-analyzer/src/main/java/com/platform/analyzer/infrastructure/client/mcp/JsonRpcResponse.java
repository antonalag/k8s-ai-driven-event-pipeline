package com.platform.analyzer.infrastructure.client.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Inbound JSON-RPC 2.0 response from the MCP Server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcResponse(
        String jsonrpc,
        String id,
        JsonRpcResult result,
        JsonRpcError error
) {
    /**
     * Returns true if the response contains an error.
     */
    public boolean isError() {
        return error != null;
    }

    /**
     * Returns true if the response represents a successful tool invocation.
     */
    public boolean isSuccess() {
        return result != null && error == null;
    }
}
