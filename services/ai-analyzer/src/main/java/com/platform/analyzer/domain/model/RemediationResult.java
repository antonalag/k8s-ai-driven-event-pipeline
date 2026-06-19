package com.platform.analyzer.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * Sealed interface representing the outcome of a remediation operation.
 * Either a {@link Success} (mutation applied) or a {@link Failure} (mutation rejected/failed).
 */
public sealed interface RemediationResult permits RemediationResult.Success, RemediationResult.Failure {

    /**
     * Successful remediation execution.
     *
     * @param action the tool that was executed (e.g., "restart_deployment")
     * @param timestamp when the mutation was applied
     * @param details additional tool-specific metadata (e.g., previousReplicas, newImage)
     */
    record Success(
            String action,
            Instant timestamp,
            Map<String, Object> details
    ) implements RemediationResult {
        public Success {
            if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
            if (timestamp == null) throw new IllegalArgumentException("timestamp must not be null");
            if (details == null) details = Map.of();
        }
    }

    /**
     * Failed remediation execution.
     *
     * @param action the tool that was attempted
     * @param errorCode machine-readable error code (e.g., "RESOURCE_NOT_FOUND", "CIRCUIT_OPEN")
     * @param errorMessage human-readable error description
     * @param timestamp when the failure occurred
     */
    record Failure(
            String action,
            String errorCode,
            String errorMessage,
            Instant timestamp
    ) implements RemediationResult {
        public Failure {
            if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
            if (errorCode == null || errorCode.isBlank()) throw new IllegalArgumentException("errorCode must not be blank");
            if (errorMessage == null || errorMessage.isBlank()) throw new IllegalArgumentException("errorMessage must not be blank");
            if (timestamp == null) throw new IllegalArgumentException("timestamp must not be null");
        }
    }
}
