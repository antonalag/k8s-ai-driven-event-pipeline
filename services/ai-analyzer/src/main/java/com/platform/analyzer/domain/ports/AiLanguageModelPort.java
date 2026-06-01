package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;

/**
 * Port for executing AI analysis on Kubernetes events.
 * Implementations (Ollama, OpenAI, etc.) reside in infrastructure/.
 */
public interface AiLanguageModelPort {

    AiAnalysis analyze(KubernetesEvent event);
}
