package com.platform.analyzer.domain.ports;

/**
 * Unchecked exception thrown when a remediation adapter encounters an infrastructure failure
 * (connection refused, timeout, unexpected HTTP error from the MCP Server).
 *
 * <p>This exception is recorded as a failure by the Mutation Circuit Breaker.
 */
public class RemediationException extends RuntimeException {

    public RemediationException(String message) {
        super(message);
    }

    public RemediationException(String message, Throwable cause) {
        super(message, cause);
    }
}
