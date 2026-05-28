package com.platform.analyzer.infrastructure;

import com.platform.analyzer.model.AiAnalysisDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data OpenSearch repository for {@link AiAnalysisDocument} entities.
 *
 * <p>Backed by the {@code ai-analysis-reports} OpenSearch index (declared via
 * {@code @Document} on the entity). Spring Data generates the CRUD implementation
 * at startup — no boilerplate required.
 *
 * <p>The {@link ElasticsearchRepository} base interface is used here because
 * {@code spring-data-opensearch} is API-compatible with Spring Data Elasticsearch
 * and reuses its repository infrastructure.
 *
 * <p>Custom query methods can be added here following Spring Data naming conventions
 * (e.g. {@code findByVerdict}, {@code findByPodNameAndNamespace}) as the platform evolves.
 */
@Repository
public interface AiAnalysisRepository extends ElasticsearchRepository<AiAnalysisDocument, String> {

    /**
     * Retrieves all analysis documents for a given Pod name, ordered by analysis time
     * descending (most recent first). Useful for per-pod history queries.
     *
     * @param podName the Kubernetes Pod name to query
     * @return list of analysis documents for the pod, newest first
     */
    List<AiAnalysisDocument> findByPodNameOrderByAnalyzedAtDesc(String podName);

    /**
     * Retrieves all analysis documents with a specific AI verdict.
     * Enables filtering dashboards by health classification.
     *
     * @param verdict one of HEALTHY, TRANSIENT_ISSUE, CRITICAL_FAILURE
     * @return list of documents matching the verdict
     */
    List<AiAnalysisDocument> findByVerdict(String verdict);
}
