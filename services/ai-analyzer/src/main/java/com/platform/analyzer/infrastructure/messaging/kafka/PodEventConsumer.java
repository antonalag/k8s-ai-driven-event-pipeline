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

/**
 * Kafka consumer that drives the intelligence pipeline for each KubernetesEvent.
 */
@Component
@ConditionalOnProperty(name = "platform.messaging.type", havingValue = "kafka")
public class PodEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PodEventConsumer.class);

    private final PodAnalyzerService analyzerService;
    private final LmMessagingPort messagingPort;
    private final ObjectMapper objectMapper;

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

        if (!requiresAnalysis(event.status())) {
            log.debug("[SKIP] Pod '{}' is {} — no analysis needed.", event.podName(), event.status());
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

    private String prettyPrint(AiAnalysis analysis) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(analysis);
        } catch (JsonProcessingException e) {
            log.warn("Could not pretty-print AiAnalysis, falling back to toString()", e);
            return analysis.toString();
        }
    }
}
