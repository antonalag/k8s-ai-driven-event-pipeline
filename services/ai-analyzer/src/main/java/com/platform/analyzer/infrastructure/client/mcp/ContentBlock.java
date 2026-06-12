package com.platform.analyzer.infrastructure.client.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Content block within a JSON-RPC 2.0 result from the MCP Server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContentBlock(String type, String text) {}
