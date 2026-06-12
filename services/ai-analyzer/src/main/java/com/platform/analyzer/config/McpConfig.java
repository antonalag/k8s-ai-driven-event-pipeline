package com.platform.analyzer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activates MCP connection configuration properties.
 * The McpProperties record is bound to the {@code platform.mcp.*} prefix.
 */
@Configuration
@EnableConfigurationProperties({McpProperties.class, McpCircuitBreakerProperties.class})
public class McpConfig {
}
