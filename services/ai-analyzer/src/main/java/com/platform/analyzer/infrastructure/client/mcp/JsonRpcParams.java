package com.platform.analyzer.infrastructure.client.mcp;

import java.util.Map;

/**
 * JSON-RPC 2.0 params container for MCP tool invocations.
 */
public record JsonRpcParams(
        String name,
        Map<String, Object> arguments
) {}
