package com.platform.analyzer.domain.port.outbound;

import com.platform.analyzer.domain.model.valueobject.AiAnalysis;

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
