package com.k8s.pipeline.collector.model;

import java.time.Instant;

/**
 * Immutable data contract for a Kubernetes Pod state change event.
 *
 * <p>This record is the direct Java representation of the schema defined in
 * {@code specs/schemas/k8s-event.v1.json}. All fields are required and
 * non-null, mirroring the {@code "required"} and {@code "additionalProperties": false}
 * constraints of the JSON Schema contract.
 *
 * <p>Field mapping:
 * <ul>
 *   <li>{@code podName}   → JSON {@code podName}   (string, minLength: 1)</li>
 *   <li>{@code namespace} → JSON {@code namespace} (string, minLength: 1)</li>
 *   <li>{@code status}    → JSON {@code status}    (enum: Pending|Running|Succeeded|Failed|Unknown)</li>
 *   <li>{@code timestamp} → JSON {@code timestamp} (string, format: date-time → {@link Instant})</li>
 * </ul>
 *
 * @param podName   The name of the Kubernetes Pod that triggered the event.
 * @param namespace The Kubernetes namespace in which the Pod resides.
 * @param status    The current phase of the Pod lifecycle.
 * @param timestamp The UTC instant at which the event was observed by the collector.
 */
public record KubernetesEvent(
        String podName,
        String namespace,
        PodPhase status,
        Instant timestamp
) {
    /**
     * Compact canonical constructor — enforces non-null contract on all fields.
     */
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
