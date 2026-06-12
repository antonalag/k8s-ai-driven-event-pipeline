package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;

import java.util.List;

/**
 * Port for executing AI analysis on Kubernetes events.
 * Implementations (Ollama, OpenAI, etc.) reside in infrastructure/.
 */
public interface AiLanguageModelPort {

    /**
     * Analyze a Kubernetes event using AI with historical context and enriched MCP context.
     */
    AiAnalysis analyze(KubernetesEvent event, List<AiAnalysis> history, EnrichedContext context);

    /**
     * Backward-compatible overload — delegates to the enriched version with empty context.
     */
    default AiAnalysis analyze(KubernetesEvent event, List<AiAnalysis> history) {
        return analyze(event, history, EnrichedContext.EMPTY);
    }
}
