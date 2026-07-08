package com.platform.analyzer.infrastructure.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.AiAnalysisEvent;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import com.platform.analyzer.domain.ports.LmMessagingPort;
import com.platform.analyzer.service.PodAnalyzerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka consumer that drives the intelligence pipeline for each KubernetesEvent.
 * Applies a per-deployment cooldown to prevent redundant analyses during pod transitions.
 */
@Component
@ConditionalOnProperty(name = "platform.messaging.type", havingValue = "kafka")
public class PodEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PodEventConsumer.class);
    private static final long ANALYSIS_COOLDOWN_MS = 60_000;

    private final PodAnalyzerService analyzerService;
    private final LmMessagingPort messagingPort;
    private final ObjectMapper objectMapper;
    private final Map<String, Instant> lastAnalysisTimestamps = new ConcurrentHashMap<>();

    public PodEventConsumer(
            PodAnalyzerService analyzerService,
            LmMessagingPort messagingPort,
            ObjectMapper objectMapper) {
        this.analyzerService = analyzerService;
        this.messagingPort = messagingPort;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "k8s-pod-events",
            groupId = "ai-analyzer-group"
    )
    public void onPodEvent(KubernetesEvent event) {
        log.info("[KAFKA] Event received — pod='{}' namespace='{}' status='{}'",
                event.podName(), event.namespace(), event.status());

        // Pods transitioning to Running/Succeeded → publish HEALTHY verdict (closes the loop)
        if (isResolved(event.status())) {
            log.info("[RESOLVED] Pod '{}' is now {} — publishing HEALTHY verdict.",
                    event.podName(), event.status());
            publishHealthyVerdict(event);
            clearCooldown(extractDeploymentPrefix(event.podName()));
            return;
        }

        if (!requiresAnalysis(event.status())) {
            log.debug("[SKIP] Pod '{}' is {} — no analysis needed.", event.podName(), event.status());
            return;
        }

        String deploymentKey = extractDeploymentPrefix(event.podName());
        if (isWithinCooldown(deploymentKey)) {
            log.debug("[COOLDOWN] Deployment '{}' analysed recently — skipping duplicate.", deploymentKey);
            return;
        }

        log.info("[ANALYZE] Forwarding pod '{}' ({}) to AI for diagnosis...",
                event.podName(), event.status());

        try {
            AiAnalysis analysis = analyzerService.analyse(event);
            log.info("[DIAGNOSIS] AI analysis for pod '{}':\n{}",
                    event.podName(), prettyPrint(analysis));

            AiAnalysisEvent outboundEvent = AiAnalysisEvent.from(analysis, event);
            messagingPort.publish(outboundEvent);
            recordAnalysis(deploymentKey);

        } catch (AiAnalysisException e) {
            log.error("[ERROR] AI analysis failed for pod '{}': {}", event.podName(), e.getMessage(), e);
        }
    }

    private boolean requiresAnalysis(PodPhase status) {
        return switch (status) {
            case Failed, Pending, Unknown -> true;
            case Running, Succeeded -> false;
        };
    }

    private boolean isResolved(PodPhase status) {
        return status == PodPhase.Running || status == PodPhase.Succeeded;
    }

    /**
     * Publishes a HEALTHY verdict directly (no AI invocation) to close the diagnostic loop.
     * This allows the query endpoint to filter out pods that have recovered.
     */
    private void publishHealthyVerdict(KubernetesEvent event) {
        AiAnalysis healthyAnalysis = new AiAnalysis(
                event.podName(),
                event.namespace(),
                "HEALTHY",
                "Pod recovered — now in " + event.status() + " state.",
                List.of()
        );

        AiAnalysisEvent outboundEvent = AiAnalysisEvent.from(healthyAnalysis, event);
        messagingPort.publish(outboundEvent);
    }

    private String prettyPrint(AiAnalysis analysis) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(analysis);
        } catch (JsonProcessingException e) {
            log.warn("Could not pretty-print AiAnalysis, falling back to toString()", e);
            return analysis.toString();
        }
    }

    /**
     * Checks if the given deployment was analysed within the cooldown period.
     * Prevents redundant Ollama invocations during pod state transitions.
     */
    private boolean isWithinCooldown(String deploymentKey) {
        Instant lastAnalysis = lastAnalysisTimestamps.get(deploymentKey);
        if (lastAnalysis == null) return false;
        return Instant.now().toEpochMilli() - lastAnalysis.toEpochMilli() < ANALYSIS_COOLDOWN_MS;
    }

    private void recordAnalysis(String deploymentKey) {
        lastAnalysisTimestamps.put(deploymentKey, Instant.now());
    }

    /**
     * Clears cooldown when a pod is resolved, allowing fresh analysis if it fails again.
     */
    private void clearCooldown(String deploymentKey) {
        lastAnalysisTimestamps.remove(deploymentKey);
    }

    /**
     * Extracts the deployment prefix from a pod name.
     * Removes the last two hyphen-separated segments (ReplicaSet hash + pod hash).
     */
    private String extractDeploymentPrefix(String podName) {
        int lastDash = podName.lastIndexOf('-');
        if (lastDash > 0) {
            int secondLastDash = podName.lastIndexOf('-', lastDash - 1);
            if (secondLastDash > 0) {
                return podName.substring(0, secondLastDash);
            }
        }
        return podName;
    }
}
