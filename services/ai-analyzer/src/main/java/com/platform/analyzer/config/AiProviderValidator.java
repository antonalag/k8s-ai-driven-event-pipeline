package com.platform.analyzer.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Validates {@code platform.ai.provider} on startup.
 * Aborts the ApplicationContext if the value is missing or invalid (fail-fast).
 */
@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class AiProviderValidator {

    private static final Logger log = LoggerFactory.getLogger(AiProviderValidator.class);
    private static final Set<String> ALLOWED_PROVIDERS = Set.of("ollama", "byok");

    private final PlatformProperties platformProperties;

    public AiProviderValidator(PlatformProperties platformProperties) {
        this.platformProperties = platformProperties;
    }

    @PostConstruct
    void validate() {
        String provider = resolveProvider();

        if (provider == null || provider.isBlank()) {
            String msg = "Property 'platform.ai.provider' is not defined or is empty. "
                    + "Allowed values: " + ALLOWED_PROVIDERS;
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        if (!ALLOWED_PROVIDERS.contains(provider)) {
            String msg = "Invalid value '%s' for property 'platform.ai.provider'. "
                    .formatted(provider)
                    + "Allowed values: " + ALLOWED_PROVIDERS;
            log.error(msg);
            throw new IllegalStateException(msg);
        }
    }

    private String resolveProvider() {
        if (platformProperties == null || platformProperties.ai() == null) {
            return null;
        }
        return platformProperties.ai().provider();
    }
}
