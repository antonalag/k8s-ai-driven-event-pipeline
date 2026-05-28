package com.platform.analyzer.model;

/**
 * Represents the standard Kubernetes Pod lifecycle phases.
 * Mirrors the {@code status.enum} values defined in
 * {@code specs/schemas/k8s-event.v1.json}.
 */
public enum PodPhase {
    Pending,
    Running,
    Succeeded,
    Failed,
    Unknown
}
