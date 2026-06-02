package com.platform.analyzer.config;

import com.platform.analyzer.infrastructure.client.byok.ProviderType;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration properties for the BYOK (Bring Your Own Key) AI provider.
 * Maps properties under the {@code platform.byok} prefix.
 */
@ConfigurationProperties(prefix = "platform.byok")
@Validated
public record ByokProperties(
        @NotBlank String endpoint,
        @NotBlank String apiKey,
        @NotBlank String model,
        ProviderType providerType
) {
    public ByokProperties {
        if (providerType == null) {
            providerType = ProviderType.OPENAI_COMPATIBLE;
        }
    }
}
