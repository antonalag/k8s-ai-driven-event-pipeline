package com.platform.analyzer.domain.port.outbound;

import com.platform.analyzer.domain.model.event.AnalysisLifecycleEvent;

/**
 * Outbound port for publishing analysis lifecycle state-change events.
 */
public interface LifecycleMessagingPort {

    void publishLifecycleEvent(AnalysisLifecycleEvent event);
}
