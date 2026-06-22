package com.platform.analyzer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Explicit repository scanning for Spring Data OpenSearch.
 * Required because we exclude Spring Boot's native ElasticsearchDataAutoConfiguration
 * to avoid class loading conflicts with co.elastic.clients.ApiClient.
 */
@Configuration
@EnableElasticsearchRepositories(
        basePackages = "com.platform.analyzer.infrastructure.persistence.opensearch"
)
public class OpenSearchRepositoryConfig {
}
