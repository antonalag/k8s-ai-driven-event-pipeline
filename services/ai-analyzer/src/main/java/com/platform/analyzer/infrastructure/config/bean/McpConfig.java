package com.platform.analyzer.infrastructure.config.bean;

import com.platform.analyzer.infrastructure.config.properties.McpCircuitBreakerProperties;
import com.platform.analyzer.infrastructure.config.properties.McpProperties;
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
