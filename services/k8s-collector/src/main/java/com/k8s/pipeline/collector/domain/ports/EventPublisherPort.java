package com.k8s.pipeline.collector.domain.ports;

import com.k8s.pipeline.collector.domain.model.KubernetesEvent;

/**
 * Port for publishing Kubernetes events to a messaging system.
 * Implementations (Kafka, etc.) reside in infrastructure/.
 */
public interface EventPublisherPort {

    void publish(KubernetesEvent event);
}
