package com.platform.analyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for the platform adapter activation.
 * Maps properties under the {@code platform.*} prefix.
 */
@ConfigurationProperties(prefix = "platform")
public record PlatformProperties(
        Ai ai,
        Storage storage,
        Messaging messaging
) {
    public record Ai(String provider) {}
    public record Storage(String type) {}
    public record Messaging(String type) {}
}
