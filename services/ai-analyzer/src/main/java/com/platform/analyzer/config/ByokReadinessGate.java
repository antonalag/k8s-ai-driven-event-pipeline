package com.platform.analyzer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

/**
 * Starts Kafka listeners immediately for BYOK providers.
 * Unlike Ollama (which runs on the host and may need network stabilization),
 * BYOK endpoints are cloud-hosted and assumed always reachable.
 */
@Component
@ConditionalOnProperty(name = "platform.ai.provider", havingValue = "byok")
public class ByokReadinessGate {

    private static final Logger log = LoggerFactory.getLogger(ByokReadinessGate.class);

    private final KafkaListenerEndpointRegistry registry;

    public ByokReadinessGate(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startListeners() {
        log.info("[READINESS] BYOK provider configured. Starting Kafka consumers immediately.");
        registry.getAllListenerContainers().forEach(container -> {
            if (!container.isRunning()) {
                container.start();
                log.info("[READINESS] Started Kafka listener: {}", container.getListenerId());
            }
        });
    }
}
