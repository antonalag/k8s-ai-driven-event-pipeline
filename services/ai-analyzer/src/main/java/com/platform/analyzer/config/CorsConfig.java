package com.platform.analyzer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for direct browser→backend access (development without Docker).
 *
 * In containerized deployments, Nginx reverse-proxy eliminates the need for CORS
 * by serving both the SPA and API from the same origin. This configuration is a
 * fallback for local development where the UI runs on port 3000 and the backend
 * on port 8082 directly.
 *
 * Security: Only http://localhost:3000 is permitted — no wildcards.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
