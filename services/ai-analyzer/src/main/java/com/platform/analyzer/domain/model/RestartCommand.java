package com.platform.analyzer.domain.model;

import java.util.UUID;

/**
 * Command to perform a rolling restart of a Kubernetes deployment.
 *
 * @param correlationId UUID inherited from the original AiAnalysis failure report
 * @param deploymentName name of the Kubernetes deployment to restart
 * @param namespace target Kubernetes namespace
 */
public record RestartCommand(
        UUID correlationId,
        String deploymentName,
        String namespace
) {
    public RestartCommand {
        if (correlationId == null) throw new IllegalArgumentException("correlationId must not be null");
        if (deploymentName == null || deploymentName.isBlank()) throw new IllegalArgumentException("deploymentName must not be blank");
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("namespace must not be blank");
    }
}
