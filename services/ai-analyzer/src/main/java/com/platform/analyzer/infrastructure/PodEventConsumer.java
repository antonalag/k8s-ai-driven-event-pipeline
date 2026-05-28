package com.platform.analyzer.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.model.AiAnalysis;
import com.platform.analyzer.model.AiAnalysisEvent;
import com.platform.analyzer.model.KubernetesEvent;
import com.platform.analyzer.model.PodPhase;
import com.platform.analyzer.service.OllamaAnalyzerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that drives the full intelligence pipeline for each received
 * {@link KubernetesEvent}:
 *
 * <ol>
 *   <li>Receives a Pod event from the {@code k8s-pod-events} topic.</li>
 *   <li>Routes only non-healthy events (Failed / Pending / Unknown) to the AI analyzer.</li>
 *   <li>Forwards the structured {@link AiAnalysis} to {@link AiAnalysisProducer} for
 *       publication to the {@code ai-analysis-events} topic.</li>
 * </ol>
 *
 * <p>This makes {@code ai-analyzer} a hybrid Consumer+Producer service:
 * <ul>
 *   <li><strong>Consumes</strong> from {@code k8s-pod-events}</li>
 *   <li><strong>Produces</strong> to {@code ai-analysis-events}</li>
 * </ul>
 */
@Component
public class PodEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PodEventConsumer.class);

    private final OllamaAnalyzerService analyzerService;
    private final AiAnalysisProducer analysisProducer;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection — preferred over field injection per project standards.
     *
     * @param analyzerService  the Ollama-backed AI analysis service
     * @param analysisProducer the Kafka producer for {@code ai-analysis-events}
     * @param objectMapper     shared Jackson mapper for pretty-printing the diagnosis log
     */
    public PodEventConsumer(
            OllamaAnalyzerService analyzerService,
            AiAnalysisProducer analysisProducer,
            ObjectMapper objectMapper) {
        this.analyzerService = analyzerService;
        this.analysisProducer = analysisProducer;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives a {@link KubernetesEvent} from the {@code k8s-pod-events} topic and
     * drives the full analysis pipeline.
     *
     * <p>Healthy pods (Running, Succeeded) are skipped to avoid unnecessary LLM load.
     * For non-healthy pods, the AI diagnosis is obtained, logged, and published to
     * {@code ai-analysis-events}.
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

            // Build the outbound event and publish it to ai-analysis-events
            AiAnalysisEvent outboundEvent = AiAnalysisEvent.from(analysis, event);
            analysisProducer.publish(outboundEvent);

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
