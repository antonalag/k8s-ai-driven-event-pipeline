package com.platform.analyzer.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Inbound DTO for remediation requests via POST /api/v1/remediations.
 * Validated with JSR-380 annotations before reaching the orchestrator.
 *
 * @param correlationId UUID linking to the original AiAnalysis failure report
 * @param action one of: restart_deployment, scale_deployment, fix_container_image
 * @param deploymentName Kubernetes deployment name (1-253 chars)
 * @param namespace Kubernetes namespace (1-63 chars)
 * @param replicas desired replicas (optional, required for scale_deployment, bounded 0-10)
 * @param containerName container name (optional, required for fix_container_image)
 * @param correctImage correct image reference (optional, required for fix_container_image)
 */
public record RemediationRequest(

        @NotNull(message = "correlationId is required")
        UUID correlationId,

        @NotBlank(message = "action is required")
        @Pattern(regexp = "restart_deployment|scale_deployment|fix_container_image",
                message = "action must be one of: restart_deployment, scale_deployment, fix_container_image")
        String action,

        @NotBlank(message = "deploymentName is required")
        @Size(min = 1, max = 253, message = "deploymentName must be between 1 and 253 characters")
        String deploymentName,

        @NotBlank(message = "namespace is required")
        @Size(min = 1, max = 63, message = "namespace must be between 1 and 63 characters")
        String namespace,

        @Min(value = 0, message = "replicas must be >= 0")
        @Max(value = 10, message = "replicas must be <= 10")
        Integer replicas,

        @Size(max = 63, message = "containerName must not exceed 63 characters")
        String containerName,

        @Size(max = 512, message = "correctImage must not exceed 512 characters")
        String correctImage

) {}
