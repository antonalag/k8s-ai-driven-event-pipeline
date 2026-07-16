package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.AnalysisLifecycleEvent;

/**
 * Outbound port for publishing analysis lifecycle state-change events.
 */
public interface LifecycleMessagingPort {

    void publishLifecycleEvent(AnalysisLifecycleEvent event);
}
