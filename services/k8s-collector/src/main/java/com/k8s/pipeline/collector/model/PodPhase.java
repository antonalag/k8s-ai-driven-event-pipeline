package com.k8s.pipeline.collector.model;

/**
 * Represents the standard Kubernetes Pod lifecycle phases.
 * Maps 1-to-1 with the {@code status.enum} values defined in
 * {@code specs/schemas/k8s-event.v1.json}.
 *
 * @see <a href="https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-phase">
 *      Kubernetes Pod Phase documentation</a>
 */
public enum PodPhase {
    Pending,
    Running,
    Succeeded,
    Failed,
    Unknown
}
