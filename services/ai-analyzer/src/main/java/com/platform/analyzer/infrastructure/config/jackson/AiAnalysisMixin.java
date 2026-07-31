package com.platform.analyzer.infrastructure.config.jackson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Jackson mixin that applies {@code @JsonIgnoreProperties(ignoreUnknown = true)}
 * to the domain value object {@code AiAnalysis} without polluting the domain layer
 * with framework annotations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AiAnalysisMixin {
}
