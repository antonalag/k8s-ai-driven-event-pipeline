package com.k8s.pipeline.collector.domain.ports;

/**
 * Unchecked exception thrown when event publication fails.
 */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message) {
        super(message);
    }

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
