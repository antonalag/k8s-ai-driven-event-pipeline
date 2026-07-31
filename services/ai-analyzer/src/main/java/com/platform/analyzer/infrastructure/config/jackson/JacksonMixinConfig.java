package com.platform.analyzer.infrastructure.config.jackson;

import com.platform.analyzer.domain.model.valueobject.AiAnalysis;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Jackson mixins for domain value objects, keeping the domain layer
 * free of serialization annotations.
 */
@Configuration
public class JacksonMixinConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer aiAnalysisMixinCustomizer() {
        return builder -> builder.mixIn(AiAnalysis.class, AiAnalysisMixin.class);
    }
}
