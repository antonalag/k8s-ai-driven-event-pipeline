package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.AnalysisLifecycleEvent;

/**
 * Outbound port for publishing analysis lifecycle state-change events.
 *
 * <p>Zero Spring imports — pure domain.</p>
 */
public interface LifecycleMessagingPort {

    /**
     * Publishes a lifecycle event to the messaging infrastructure.
     *
     * @param event the lifecycle event to publish
     */
    void publishLifecycleEvent(AnalysisLifecycleEvent event);
}
