package com.platform.analyzer.infrastructure.web.dto;

/**
 * Inbound DTO for analysis dismissal requests via POST /api/v1/analyses/{id}/dismiss.
 * The request body is optional — a null body implies no explicit reason.
 *
 * @param reason optional operator-provided reason for the dismissal
 */
public record DismissRequest(String reason) {
}
