package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.ports.AiAnalysisRepositoryPort;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import com.platform.analyzer.domain.ports.McpContextPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service that orchestrates AI analysis of Kubernetes events.
 * Depends EXCLUSIVELY on port interfaces — never on infrastructure classes.
 */
@Service
public class OllamaAnalyzerService {

    private final AiLanguageModelPort aiLanguageModel;
    private final AiAnalysisRepositoryPort aiAnalysisRepositoryPort;
    private final McpContextPort mcpContextPort;

    public OllamaAnalyzerService(AiLanguageModelPort aiLanguageModel,
                                 AiAnalysisRepositoryPort aiAnalysisRepositoryPort,
                                 McpContextPort mcpContextPort) {
        this.aiLanguageModel = aiLanguageModel;
        this.aiAnalysisRepositoryPort = aiAnalysisRepositoryPort;
        this.mcpContextPort = mcpContextPort;
    }

    public AiAnalysis analyse(KubernetesEvent event) {
        List<AiAnalysis> history = aiAnalysisRepositoryPort.findByPodName(event.podName());
        EnrichedContext context = mcpContextPort.retrieveContext(event.podName(), event.namespace());
        return aiLanguageModel.analyze(event, history, context);
    }
}
