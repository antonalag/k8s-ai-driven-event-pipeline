package com.platform.analyzer.infrastructure.client.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Error object within a JSON-RPC 2.0 error response from the MCP Server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcError(int code, String message) {}
