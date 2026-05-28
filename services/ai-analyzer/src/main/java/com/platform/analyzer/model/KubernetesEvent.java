package com.platform.analyzer.model;

import java.time.Instant;

/**
 * Inbound data contract for a Kubernetes Pod state change event.
 *
 * <p>This record mirrors the schema defined in {@code specs/schemas/k8s-event.v1.json}
 * and is the deserialized form of messages consumed from the {@code k8s-pod-events} topic.
 * It is intentionally kept as a local copy — the ai-analyzer service owns its own model
 * and does not depend on the k8s-collector module.
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
) {}
