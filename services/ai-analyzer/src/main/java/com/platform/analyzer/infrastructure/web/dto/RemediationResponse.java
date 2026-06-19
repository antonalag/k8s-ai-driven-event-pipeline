package com.platform.analyzer.infrastructure.web.dto;

import com.platform.analyzer.domain.model.RemediationResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound DTO for successful remediation responses.
 *
 * @param correlationId UUID linking to the original failure
 * @param action the tool that was executed
 * @param status always "completed" for success responses
 * @param timestamp when the mutation was applied (ISO-8601)
 * @param details tool-specific metadata
 */
public record RemediationResponse(
        UUID correlationId,
        String action,
        String status,
        Instant timestamp,
        Map<String, Object> details
) {
    /**
     * Factory method to build a response from a successful RemediationResult.
     */
    public static RemediationResponse from(UUID correlationId, RemediationResult.Success success) {
        return new RemediationResponse(
                correlationId,
                success.action(),
                "completed",
                success.timestamp(),
                success.details()
        );
    }
}
