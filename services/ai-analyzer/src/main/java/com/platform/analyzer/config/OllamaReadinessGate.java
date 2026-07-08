package com.platform.analyzer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Delays Kafka listener startup until the AI provider is reachable.
 * Prevents the circuit breaker from opening during container startup when the
 * host network may not be fully routable yet (Docker → host via UFW).
 *
 * Runs the probe on a dedicated thread to avoid blocking other ApplicationReadyEvent listeners.
 * Only active when platform.ai.provider=ollama.
 */
@Component
@ConditionalOnProperty(name = "platform.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaReadinessGate {

    private static final Logger log = LoggerFactory.getLogger(OllamaReadinessGate.class);
    private static final int MAX_ATTEMPTS = 15;
    private static final int DELAY_MS = 3000;

    private final KafkaListenerEndpointRegistry registry;
    private final String ollamaUrl;

    public OllamaReadinessGate(
            KafkaListenerEndpointRegistry registry,
            @Value("${ollama.api.url}") String ollamaUrl) {
        this.registry = registry;
        this.ollamaUrl = ollamaUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread.ofVirtual().name("ollama-readiness-probe").start(this::probeAndStartListeners);
    }

    private void probeAndStartListeners() {
        log.info("[READINESS] Probing Ollama at {} before starting Kafka consumers...", ollamaUrl);

        boolean reachable = false;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                RestClient.create(ollamaUrl)
                        .get()
                        .uri("/api/tags")
                        .retrieve()
                        .toBodilessEntity();
                reachable = true;
                log.info("[READINESS] Ollama reachable after {} attempt(s). Starting Kafka consumers.", attempt);
                break;
            } catch (Exception e) {
                log.warn("[READINESS] Ollama not reachable (attempt {}/{}): {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!reachable) {
            log.error("[READINESS] Ollama not reachable after {} attempts ({})." +
                    " Consumers will start but circuit breaker will produce DEGRADED results.",
                    MAX_ATTEMPTS, ollamaUrl);
        }

        registry.getAllListenerContainers().forEach(container -> {
            if (!container.isRunning()) {
                container.start();
                log.info("[READINESS] Started Kafka listener: {}", container.getListenerId());
            }
        });
    }
}
