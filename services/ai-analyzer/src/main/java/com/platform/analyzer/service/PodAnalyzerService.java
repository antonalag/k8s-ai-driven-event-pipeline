package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.ports.AiAnalysisRepositoryPort;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import com.platform.analyzer.domain.ports.CircuitBreakerStatePort;
import com.platform.analyzer.domain.ports.McpContextPort;
import com.platform.analyzer.domain.ports.PipelineTracer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Application service that orchestrates AI analysis of Kubernetes events.
 * Integrates pipeline tracing for E2E observability (correlation IDs,
 * circuit breaker state, per-tool timing, and threshold monitoring).
 */
@Service
public class PodAnalyzerService {

    private static final long THRESHOLD_MS = 30_000;

    private final AiLanguageModelPort aiLanguageModel;
    private final AiAnalysisRepositoryPort aiAnalysisRepositoryPort;
    private final McpContextPort mcpContextPort;
    private final PipelineTracer pipelineTracer;
    private final CircuitBreakerStatePort circuitBreakerStatePort;

    public PodAnalyzerService(AiLanguageModelPort aiLanguageModel,
                                 AiAnalysisRepositoryPort aiAnalysisRepositoryPort,
                                 McpContextPort mcpContextPort,
                                 PipelineTracer pipelineTracer,
                                 CircuitBreakerStatePort circuitBreakerStatePort) {
        this.aiLanguageModel = aiLanguageModel;
        this.aiAnalysisRepositoryPort = aiAnalysisRepositoryPort;
        this.mcpContextPort = mcpContextPort;
        this.pipelineTracer = pipelineTracer;
        this.circuitBreakerStatePort = circuitBreakerStatePort;
    }

    public AiAnalysis analyse(KubernetesEvent event) {
        String correlationId = UUID.randomUUID().toString();
        long cycleStart = System.currentTimeMillis();

        // Log cycle start with circuit breaker state
        String cbState = circuitBreakerStatePort.getMcpCircuitBreakerState();
        pipelineTracer.logCycleStart(correlationId, cbState, event.podName(), event.namespace());

        // Retrieve history
        List<AiAnalysis> history = aiAnalysisRepositoryPort.findByPodName(event.podName());

        // MCP enrichment with timing
        long mcpStart = System.currentTimeMillis();
        EnrichedContext context = mcpContextPort.retrieveContext(event.podName(), event.namespace());
        long mcpElapsed = System.currentTimeMillis() - mcpStart;

        // Log per-tool results inferred from EnrichedContext
        logToolResults(correlationId, context, mcpElapsed);

        // AI analysis
        AiAnalysis result = aiLanguageModel.analyze(event, history, context);

        // Cycle completion
        long totalTime = System.currentTimeMillis() - cycleStart;
        pipelineTracer.logCycleComplete(correlationId, event.podName(), event.namespace(),
                context.toolsUsed().size(), totalTime, result.verdict());

        // Threshold check — emit WARN if cycle exceeds 30 seconds
        if (totalTime > THRESHOLD_MS) {
            pipelineTracer.logThresholdExceeded(correlationId, totalTime);
        }

        return result;
    }

    private void logToolResults(String correlationId, EnrichedContext context, long mcpElapsed) {
        List<String> allTools = List.of("describe_pod", "get_events", "get_logs");
        long avgTime = context.toolsUsed().isEmpty() ? 0 : mcpElapsed / context.toolsUsed().size();

        for (String tool : allTools) {
            boolean success = context.toolsUsed().contains(tool);
            pipelineTracer.logToolResult(correlationId, tool, success ? avgTime : 0, success);
        }
    }
}
