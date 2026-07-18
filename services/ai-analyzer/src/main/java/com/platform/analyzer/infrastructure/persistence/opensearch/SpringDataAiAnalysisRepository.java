package com.platform.analyzer.infrastructure.persistence.opensearch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data OpenSearch repository for AiAnalysisDocument entities.
 */
@Repository
public interface SpringDataAiAnalysisRepository extends ElasticsearchRepository<AiAnalysisDocument, String> {

    List<AiAnalysisDocument> findByPodNameOrderByAnalyzedAtDesc(String podName);

    List<AiAnalysisDocument> findByVerdict(String verdict);

    List<AiAnalysisDocument> findByNamespaceOrderByAnalyzedAtDesc(String namespace);

    Page<AiAnalysisDocument> findByStatusIn(List<String> statuses, Pageable pageable);
}
