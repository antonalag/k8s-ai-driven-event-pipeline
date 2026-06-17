package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;

/**
 * Port for calibrating the LLM prompt with failure-typology-specific instructions
 * to ensure recommendedActions are 100% actionable.
 */
public interface PromptCalibrationStrategy {

    /**
     * Builds calibration instructions based on the failure context.
     * These are appended to the base prompt to guide the LLM toward
     * producing concrete, actionable recommendations.
     *
     * @param event the Kubernetes event being analyzed
     * @param context the enriched MCP context (may contain failure indicators)
     * @return calibration instruction text to append to the prompt
     */
    String buildCalibratedPrompt(KubernetesEvent event, EnrichedContext context);
}
