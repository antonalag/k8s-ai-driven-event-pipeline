package com.platform.analyzer.domain.model;

import java.time.Instant;

/**
 * Inbound data contract for a Kubernetes Pod state change event.
 */
public record KubernetesEvent(
        String podName,
        String namespace,
        PodPhase status,
        Instant timestamp
) {}
