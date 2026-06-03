package com.platform.analyzer.config;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

/**
 * Decorator that wraps a real AiLanguageModelPort adapter with Resilience4j
 * Circuit Breaker logic. Produces a degraded AiAnalysis on failure or open state.
 *
 * Resides in the config layer to preserve absolute domain purity.
 */
public class ResilientAiLanguageModelAdapter implements AiLanguageModelPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientAiLanguageModelAdapter.class);

    private static final String DEGRADED_VERDICT = "DEGRADED";
    private static final String DEGRADED_ROOT_CAUSE =
            "Análisis degradado temporalmente — el proveedor de IA no está disponible";
    private static final List<String> DEGRADED_ACTIONS =
            List.of("Verificar disponibilidad del proveedor de IA");

    private final AiLanguageModelPort delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientAiLanguageModelAdapter(AiLanguageModelPort delegate,
                                           CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public AiAnalysis analyze(KubernetesEvent event, List<AiAnalysis> history) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.analyze(event, history));
        } catch (CallNotPermittedException ex) {
            log.warn("Circuit breaker OPEN — generating degraded analysis. podName={}, cbState={}",
                    event.podName(), circuitBreaker.getState());
            return buildDegradedAnalysis(event);
        } catch (AiAnalysisException ex) {
            log.warn("AI provider failure — generating degraded analysis. podName={}, cause={}",
                    event.podName(), ex.getMessage());
            return buildDegradedAnalysis(event);
        } catch (ResourceAccessException ex) {
            log.warn("Network failure — generating degraded analysis. podName={}, cause={}",
                    event.podName(), ex.getMessage());
            return buildDegradedAnalysis(event);
        }
    }

    /**
     * Produces a valid degraded AiAnalysis in constant time (&lt;1ms).
     * Pure method — no I/O, no allocations beyond the record itself.
     */
    AiAnalysis buildDegradedAnalysis(KubernetesEvent event) {
        return new AiAnalysis(
                event.podName(),
                event.namespace(),
                DEGRADED_VERDICT,
                DEGRADED_ROOT_CAUSE,
                DEGRADED_ACTIONS
        );
    }
}
