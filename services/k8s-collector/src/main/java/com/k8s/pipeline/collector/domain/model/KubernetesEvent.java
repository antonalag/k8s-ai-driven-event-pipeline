package com.k8s.pipeline.collector.domain.model;

import java.time.Instant;

/**
 * Immutable data contract for a Kubernetes Pod state change event.
 */
public record KubernetesEvent(
        String podName,
        String namespace,
        PodPhase status,
        Instant timestamp
) {
    public KubernetesEvent {
        if (podName == null || podName.isBlank()) {
            throw new IllegalArgumentException("podName must not be null or blank");
        }
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be null or blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
    }
}
