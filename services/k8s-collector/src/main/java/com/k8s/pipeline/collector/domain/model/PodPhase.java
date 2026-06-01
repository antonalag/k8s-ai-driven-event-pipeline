package com.k8s.pipeline.collector.domain.model;

/**
 * Represents the standard Kubernetes Pod lifecycle phases.
 */
public enum PodPhase {
    Pending,
    Running,
    Succeeded,
    Failed,
    Unknown
}
