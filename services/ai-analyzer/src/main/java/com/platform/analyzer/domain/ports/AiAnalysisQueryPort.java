package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.AiAnalysisView;

import java.util.List;

/**
 * Read-only port for querying AI analysis results.
 * Separated from the write port (AiAnalysisRepositoryPort) following CQRS-lite.
 * Implementations (OpenSearch, etc.) reside in infrastructure/.
 */
public interface AiAnalysisQueryPort {

    List<AiAnalysisView> findAll();

    List<AiAnalysisView> findByNamespace(String namespace);

    List<AiAnalysisView> findByPodName(String podName);
}
