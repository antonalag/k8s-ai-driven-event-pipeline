package com.platform.analyzer.infrastructure.prompt;

import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.ports.PromptCalibrationStrategy;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link PromptCalibrationStrategy}.
 * Appends failure-typology-specific calibration instructions to the LLM prompt
 * to ensure recommended actions are concrete, actionable, and correctly scoped.
 */
@Component
public class DefaultPromptCalibrationStrategy implements PromptCalibrationStrategy {

    private static final String BASE_CALIBRATION = """
            
            RECOMMENDATION CALIBRATION RULES:
            - You MUST provide between 1 and 5 recommendedActions.
            - Each action MUST be a single executable step (one kubectl command or one configuration change).
            - Each action MUST reference the specific pod name "%s" and namespace "%s".
            - Each action string MUST NOT exceed 512 characters.
            - Actions must be concrete and copy-pasteable, NOT generic advice.
            """;

    private static final String CRASHLOOP_INSTRUCTIONS = """
            
            FAILURE-SPECIFIC GUIDANCE (CrashLoopBackOff detected):
            - Include environment variable validation commands (kubectl describe, kubectl get configmap).
            - Include commands to inspect and fix the ConfigMap or Secret referenced by the container.
            - Target the specific container name from the pod description.
            - Example pattern: kubectl get configmap <name> -n <namespace> -o yaml
            """;

    private static final String OOM_INSTRUCTIONS = """
            
            FAILURE-SPECIFIC GUIDANCE (OOMKilled detected):
            - Include resource limit adjustment commands with specific numeric values.
            - Derive new memory limits from the current limits (e.g., increase by 2x or to a specific value).
            - Include kubectl patch or kubectl edit commands for resource limits.
            - Example pattern: kubectl patch deployment <name> -n <namespace> -p '{"spec":{"template":{"spec":{"containers":[{"name":"<container>","resources":{"limits":{"memory":"128Mi"}}}]}}}}'
            """;

    private static final String IMAGEPULL_INSTRUCTIONS = """
            
            FAILURE-SPECIFIC GUIDANCE (ImagePullBackOff detected):
            - Include image registry verification steps referencing the image name from the pod description.
            - Include kubectl commands to inspect or correct the image pull configuration.
            - Include commands to verify image pull secrets if applicable.
            - Example pattern: kubectl describe pod <name> -n <namespace> | grep -A5 "Image"
            """;

    @Override
    public String buildCalibratedPrompt(KubernetesEvent event, EnrichedContext context) {
        StringBuilder instructions = new StringBuilder();

        // Base calibration rules always apply
        instructions.append(BASE_CALIBRATION.formatted(event.podName(), event.namespace()));

        // Detect failure typology from context and event
        String failureType = detectFailureType(event, context);

        switch (failureType) {
            case "CrashLoopBackOff" -> instructions.append(CRASHLOOP_INSTRUCTIONS);
            case "OOMKilled" -> instructions.append(OOM_INSTRUCTIONS);
            case "ImagePullBackOff" -> instructions.append(IMAGEPULL_INSTRUCTIONS);
            default -> { /* No additional typology-specific instructions */ }
        }

        return instructions.toString();
    }

    private String detectFailureType(KubernetesEvent event, EnrichedContext context) {
        // Check enriched context for failure indicators
        if (context != null && context.podDescription() != null) {
            String desc = context.podDescription();
            if (desc.contains("CrashLoopBackOff")) return "CrashLoopBackOff";
            if (desc.contains("OOMKilled")) return "OOMKilled";
            if (desc.contains("ImagePullBackOff")) return "ImagePullBackOff";
        }
        if (context != null && context.podEvents() != null) {
            String events = context.podEvents();
            if (events.contains("CrashLoopBackOff")) return "CrashLoopBackOff";
            if (events.contains("OOMKilled")) return "OOMKilled";
            if (events.contains("ImagePullBackOff")) return "ImagePullBackOff";
        }
        // Fallback: check pod name for chaos-type hints
        String podName = event.podName();
        if (podName.contains("crashloop")) return "CrashLoopBackOff";
        if (podName.contains("oomkilled") || podName.contains("oom")) return "OOMKilled";
        if (podName.contains("imagepull")) return "ImagePullBackOff";

        return "UNKNOWN";
    }
}
