package com.platform.analyzer.infrastructure.client.byok;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * BYOK (Bring Your Own Key) adapter skeleton for external AI providers.
 * Activated when platform.ai.provider=byok.
 * Full implementation will arrive in the next phase.
 */
@Component
@ConditionalOnProperty(name = "platform.ai.provider", havingValue = "byok")
public class ByokLanguageModelAdapter implements AiLanguageModelPort {

    @Override
    public AiAnalysis analyze(KubernetesEvent event, List<AiAnalysis> history) {
        throw new UnsupportedOperationException(
                "BYOK adapter is not yet implemented. Configure platform.ai.provider=ollama or provide a full implementation in a future phase.");
    }
}
