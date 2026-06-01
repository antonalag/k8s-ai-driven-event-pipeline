package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.AiAnalysis;

import java.util.List;

/**
 * Port for persisting and querying AI analyses.
 * Implementations (OpenSearch, PostgreSQL, etc.) reside in infrastructure/.
 */
public interface AiAnalysisRepositoryPort {

    void save(AiAnalysis analysis);

    List<AiAnalysis> findByPodName(String podName);

    List<AiAnalysis> findByVerdict(String verdict);
}
