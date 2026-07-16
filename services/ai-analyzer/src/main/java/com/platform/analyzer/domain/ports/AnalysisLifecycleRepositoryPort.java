package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.AnalysisLifecycle;

import java.util.Optional;

/**
 * Outbound port for persistence operations on the {@link AnalysisLifecycle} aggregate.
 */
public interface AnalysisLifecycleRepositoryPort {

    Optional<AnalysisLifecycle> findById(String id);

    void save(AnalysisLifecycle lifecycle);

    /**
     * Directly updates the status fields of a persisted analysis document.
     * Used by event consumers for async status synchronisation.
     */
    void updateStatus(String id, String status, String resolvedAt, String resolutionReason);
}
