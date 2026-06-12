package com.platform.analyzer.infrastructure.client.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Result wrapper within a successful JSON-RPC 2.0 response from the MCP Server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcResult(List<ContentBlock> content) {}
