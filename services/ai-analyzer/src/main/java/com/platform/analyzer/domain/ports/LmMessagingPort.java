package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.AiAnalysisEvent;

/**
 * Port for publishing AI analysis events to a messaging system.
 * Implementations (Kafka, RabbitMQ, etc.) reside in infrastructure/.
 */
public interface LmMessagingPort {

    void publish(AiAnalysisEvent event);
}
