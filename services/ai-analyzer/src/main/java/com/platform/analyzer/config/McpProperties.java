package com.platform.analyzer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MCP Server connection configuration properties.
 * Bound to prefix: platform.mcp
 */
@Validated
@ConfigurationProperties(prefix = "platform.mcp")
public record McpProperties(
        @NotBlank String serverUrl,
        @Min(1) int connectionTimeout,
        @Min(1) int readTimeout,
        @Min(1) int maxPromptBytes
) {
    /**
     * Provides default values when properties are not explicitly defined.
     */
    public McpProperties {
        if (connectionTimeout <= 0) connectionTimeout = 5;
        if (readTimeout <= 0) readTimeout = 10;
        if (maxPromptBytes <= 0) maxPromptBytes = 65536;
    }
}
