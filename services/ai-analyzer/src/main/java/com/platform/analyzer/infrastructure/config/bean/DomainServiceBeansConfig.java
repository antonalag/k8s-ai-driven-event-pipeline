package com.platform.analyzer.infrastructure.config.bean;

import com.platform.analyzer.domain.port.outbound.AiAnalysisRepositoryPort;
import com.platform.analyzer.domain.port.outbound.AiLanguageModelPort;
import com.platform.analyzer.domain.port.outbound.AnalysisLifecycleRepositoryPort;
import com.platform.analyzer.domain.port.outbound.CircuitBreakerStatePort;
import com.platform.analyzer.domain.port.outbound.LifecycleMessagingPort;
import com.platform.analyzer.domain.port.outbound.McpContextPort;
import com.platform.analyzer.domain.port.outbound.PipelineTracer;
import com.platform.analyzer.domain.port.outbound.RemediationPort;
import com.platform.analyzer.domain.service.DismissAnalysisService;
import com.platform.analyzer.domain.service.PodAnalyzerService;
import com.platform.analyzer.domain.service.RemediationOrchestrator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers domain services as Spring beans from the infrastructure layer,
 * preserving domain purity by keeping framework annotations out of the domain package.
 */
@Configuration
public class DomainServiceBeansConfig {

    @Bean
    public PodAnalyzerService podAnalyzerService(
            AiLanguageModelPort aiLanguageModel,
            AiAnalysisRepositoryPort aiAnalysisRepositoryPort,
            McpContextPort mcpContextPort,
            PipelineTracer pipelineTracer,
            CircuitBreakerStatePort circuitBreakerStatePort) {
        return new PodAnalyzerService(aiLanguageModel, aiAnalysisRepositoryPort,
                mcpContextPort, pipelineTracer, circuitBreakerStatePort);
    }

    @Bean
    public DismissAnalysisService dismissAnalysisService(
            AnalysisLifecycleRepositoryPort lifecycleRepository,
            LifecycleMessagingPort messagingPort) {
        return new DismissAnalysisService(lifecycleRepository, messagingPort);
    }

    @Bean
    public RemediationOrchestrator remediationOrchestrator(
            RemediationPort remediationPort,
            AiAnalysisRepositoryPort analysisRepository) {
        return new RemediationOrchestrator(remediationPort, analysisRepository);
    }
}
