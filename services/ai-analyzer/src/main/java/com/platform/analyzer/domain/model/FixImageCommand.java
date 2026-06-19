package com.platform.analyzer.domain.model;

import java.util.UUID;

/**
 * Command to patch a container image in a Kubernetes deployment to fix ImagePullBackOff.
 *
 * @param correlationId UUID inherited from the original AiAnalysis failure report
 * @param deploymentName name of the Kubernetes deployment
 * @param namespace target Kubernetes namespace
 * @param containerName name of the container within the deployment spec
 * @param correctImage the correct container image reference to apply
 */
public record FixImageCommand(
        UUID correlationId,
        String deploymentName,
        String namespace,
        String containerName,
        String correctImage
) {
    public FixImageCommand {
        if (correlationId == null) throw new IllegalArgumentException("correlationId must not be null");
        if (deploymentName == null || deploymentName.isBlank()) throw new IllegalArgumentException("deploymentName must not be blank");
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("namespace must not be blank");
        if (containerName == null || containerName.isBlank()) throw new IllegalArgumentException("containerName must not be blank");
        if (correctImage == null || correctImage.isBlank()) throw new IllegalArgumentException("correctImage must not be blank");
    }
}
