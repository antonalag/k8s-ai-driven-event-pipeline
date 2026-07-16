package com.platform.analyzer.infrastructure.web.dto;

/**
 * Outbound DTO for successful analysis dismissal responses.
 *
 * @param id     the analysis document identifier
 * @param status the new lifecycle status (e.g. "DISMISSED")
 */
public record DismissResponse(String id, String status) {
}
