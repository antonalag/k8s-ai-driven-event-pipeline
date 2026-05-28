package com.platform.analyzer.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.model.AiAnalysis;
import com.platform.analyzer.model.KubernetesEvent;
import com.platform.analyzer.model.PodPhase;
import com.platform.analyzer.service.OllamaAnalyzerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens to the {@code k8s-pod-events} topic and drives
 * the AI analysis pipeline for each received {@link KubernetesEvent}.
 *
 * <h2>Routing logic</h2>
 * <p>Only events whose Pod status indicates a non-healthy state
 * ({@code Pending}, {@code Failed}, {@code Unknown}) are forwarded to the
 * {@link OllamaAnalyzerService}. {@code Running} and {@code Succeeded} events
 * are logged at DEBUG level and skipped — they carry no actionable signal.
 *
 * <p>This keeps Ollama calls focused on events that actually warrant diagnosis
 * and avoids unnecessary load on the local LLM for healthy pods.
 */
@Component
public class PodEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PodEventConsumer.class);

    private final OllamaAnalyzerService analyzerService;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection — preferred over field injection per project standards.
     *
     * @param analyzerService the Ollama-backed AI analysis service
     * @param objectMapper    shared Jackson mapper for pretty-printing the diagnosis log
     */
    public PodEventConsumer(OllamaAnalyzerService analyzerService, ObjectMapper objectMapper) {
        this.analyzerService = analyzerService;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives a {@link KubernetesEvent} from the {@code k8s-pod-events} topic.
     *
     * <p>Events with a non-healthy status are forwarded to the AI analyzer.
     * The resulting {@link AiAnalysis} is logged as a pretty-printed JSON object
     * so the structured diagnosis is immediately visible in the service logs.
     *
     * @param event the deserialized Kubernetes Pod event
     */
    @KafkaListener(
            topics = "k8s-pod-events",
            groupId = "ai-analyzer-group"
    )
    public void onPodEvent(KubernetesEvent event) {
        log.info("[KAFKA] Event received — pod='{}' namespace='{}' status='{}'",
                event.podName(), event.namespace(), event.status());

        if (!requiresAnalysis(event.status())) {
            log.debug("[SKIP] Pod '{}' is {} — no analysis needed.", event.podName(), event.status());
            return;
        }

        log.info("[ANALYZE] Forwarding pod '{}' ({}) to Ollama for diagnosis...",
                event.podName(), event.status());

        try {
            AiAnalysis analysis = analyzerService.analyse(event);
            log.info("[DIAGNOSIS] AI analysis for pod '{}':\n{}",
                    event.podName(), prettyPrint(analysis));
        } catch (OllamaAnalyzerService.OllamaAnalysisException e) {
            log.error("[ERROR] AI analysis failed for pod '{}': {}", event.podName(), e.getMessage(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} for Pod phases that warrant an AI diagnosis call.
     * Healthy phases (Running, Succeeded) are excluded to avoid unnecessary LLM load.
     */
    private boolean requiresAnalysis(PodPhase status) {
        return switch (status) {
            case Failed, Pending, Unknown -> true;
            case Running, Succeeded -> false;
        };
    }

    /**
     * Serialises an {@link AiAnalysis} to an indented JSON string for log readability.
     * Falls back to {@code toString()} if serialisation fails unexpectedly.
     */
    private String prettyPrint(AiAnalysis analysis) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(analysis);
        } catch (JsonProcessingException e) {
            log.warn("Could not pretty-print AiAnalysis, falling back to toString()", e);
            return analysis.toString();
        }
    }
}
