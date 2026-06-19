package com.platform.analyzer.domain.model;

import java.util.UUID;

/**
 * Command to scale a Kubernetes deployment to a specified number of replicas.
 *
 * @param correlationId UUID inherited from the original AiAnalysis failure report
 * @param deploymentName name of the Kubernetes deployment to scale
 * @param namespace target Kubernetes namespace
 * @param replicas desired replica count (bounded 0-10 for safety)
 */
public record ScaleCommand(
        UUID correlationId,
        String deploymentName,
        String namespace,
        int replicas
) {
    public ScaleCommand {
        if (correlationId == null) throw new IllegalArgumentException("correlationId must not be null");
        if (deploymentName == null || deploymentName.isBlank()) throw new IllegalArgumentException("deploymentName must not be blank");
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("namespace must not be blank");
        if (replicas < 0 || replicas > 10) throw new IllegalArgumentException("replicas must be between 0 and 10");
    }
}
