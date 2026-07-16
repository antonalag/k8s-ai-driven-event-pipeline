package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.AnalysisLifecycle;

import java.util.Optional;

/**
 * Outbound port for persistence operations on the {@link AnalysisLifecycle} aggregate.
 *
 * <p>Zero Spring imports — pure domain.</p>
 */
public interface AnalysisLifecycleRepositoryPort {

    /**
     * Retrieves an analysis lifecycle entity by its document ID.
     *
     * @param id the analysis document identifier
     * @return the lifecycle entity if found, empty otherwise
     */
    Optional<AnalysisLifecycle> findById(String id);

    /**
     * Persists the current state of the lifecycle entity.
     *
     * @param lifecycle the lifecycle entity to save
     */
    void save(AnalysisLifecycle lifecycle);

    /**
     * Directly updates the status fields of a persisted analysis document.
     * Used by event consumers for async status synchronisation.
     *
     * @param id               the analysis document identifier
     * @param status           the new status value
     * @param resolvedAt       the resolution timestamp (ISO-8601 string)
     * @param resolutionReason the reason for resolution
     */
    void updateStatus(String id, String status, String resolvedAt, String resolutionReason);
}
