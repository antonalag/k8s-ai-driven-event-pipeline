package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.model.RemediationResult;
import com.platform.analyzer.infrastructure.web.dto.RemediationRequest;
import com.platform.analyzer.infrastructure.web.dto.RemediationResponse;
import com.platform.analyzer.service.RemediationOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller exposing the remediation endpoint.
 * Accepts typed remediation requests and delegates to {@link RemediationOrchestrator}.
 *
 * <p>Responses follow RFC 7807 on failure, and return structured JSON on success.
 * The {@code X-Correlation-Id} header is always included for traceability.
 */
@RestController
@RequestMapping("/api/v1/remediations")
public class RemediationController {

    private static final URI REMEDIATION_FAILURE_TYPE =
            URI.create("urn:problem-type:remediation-failure");
    private static final URI MUTATION_CB_TYPE =
            URI.create("urn:problem-type:mutation-circuit-breaker-open");

    private final RemediationOrchestrator orchestrator;

    public RemediationController(RemediationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ResponseEntity<?> executeRemediation(@Valid @RequestBody RemediationRequest request) {

        RemediationResult result = orchestrator.execute(
                request.action(),
                request.correlationId(),
                request.deploymentName(),
                request.namespace(),
                request.replicas(),
                request.containerName(),
                request.correctImage()
        );

        return switch (result) {
            case RemediationResult.Success success -> ResponseEntity.ok()
                    .header("X-Correlation-Id", request.correlationId().toString())
                    .body(RemediationResponse.from(request.correlationId(), success));

            case RemediationResult.Failure failure -> {
                HttpStatus status = mapFailureToHttpStatus(failure.errorCode());
                ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, failure.errorMessage());
                problem.setTitle(mapFailureToTitle(failure.errorCode()));
                problem.setType(mapFailureToType(failure.errorCode()));
                problem.setProperty("correlationId", request.correlationId().toString());
                problem.setProperty("action", failure.action());
                problem.setProperty("errorCode", failure.errorCode());

                yield ResponseEntity.status(status)
                        .header("X-Correlation-Id", request.correlationId().toString())
                        .body(problem);
            }
        };
    }

    private HttpStatus mapFailureToHttpStatus(String errorCode) {
        return switch (errorCode) {
            case "CIRCUIT_OPEN" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "INVALID_PARAMS", "UNSUPPORTED_ACTION" -> HttpStatus.BAD_REQUEST;
            case "RESOURCE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private String mapFailureToTitle(String errorCode) {
        return switch (errorCode) {
            case "CIRCUIT_OPEN" -> "Mutation Circuit Breaker Open";
            case "INVALID_PARAMS" -> "Invalid Remediation Parameters";
            case "UNSUPPORTED_ACTION" -> "Unsupported Remediation Action";
            case "RESOURCE_NOT_FOUND" -> "Resource Not Found";
            case "FORBIDDEN" -> "Namespace Not Authorized";
            default -> "Remediation Upstream Failure";
        };
    }

    private URI mapFailureToType(String errorCode) {
        return switch (errorCode) {
            case "CIRCUIT_OPEN" -> MUTATION_CB_TYPE;
            default -> REMEDIATION_FAILURE_TYPE;
        };
    }
}
