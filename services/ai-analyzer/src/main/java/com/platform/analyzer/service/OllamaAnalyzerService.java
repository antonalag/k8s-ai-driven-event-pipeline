package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import org.springframework.stereotype.Service;

/**
 * Application service that orchestrates AI analysis of Kubernetes events.
 * Depends EXCLUSIVELY on port interfaces — never on infrastructure classes.
 */
@Service
public class OllamaAnalyzerService {

    private final AiLanguageModelPort aiLanguageModel;

    public OllamaAnalyzerService(AiLanguageModelPort aiLanguageModel) {
        this.aiLanguageModel = aiLanguageModel;
    }

    public AiAnalysis analyse(KubernetesEvent event) {
        return aiLanguageModel.analyze(event);
    }
}
