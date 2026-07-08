package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.ports.AiAnalysisException;
import com.platform.analyzer.domain.ports.RemediationException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised error handler for the ai-analyzer REST surface.
 * All responses conform to RFC 7807 (Problem Details for HTTP APIs)
 * using Spring Boot 3's native {@link ProblemDetail} object.
 *
 * <p><strong>Security invariant:</strong> stack traces and internal details
 * are NEVER leaked to the client.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final URI VALIDATION_TYPE =
            URI.create("urn:problem-type:validation-error");
    private static final URI CIRCUIT_BREAKER_TYPE =
            URI.create("urn:problem-type:circuit-breaker-open");
    private static final URI AI_ANALYSIS_TYPE =
            URI.create("urn:problem-type:ai-analysis-failure");
    private static final URI REMEDIATION_TYPE =
            URI.create("urn:problem-type:remediation-upstream-failure");

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more fields failed validation.");
        problem.setTitle("Validation Error");
        problem.setType(VALIDATION_TYPE);

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null
                                ? fe.getDefaultMessage()
                                : "invalid",
                        (first, second) -> first));

        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ProblemDetail handleCircuitBreakerOpen(CallNotPermittedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI analysis service is temporarily unavailable. Please retry later.");
        problem.setTitle("Service Unavailable");
        problem.setType(CIRCUIT_BREAKER_TYPE);
        problem.setProperty("circuitBreaker", ex.getCausingCircuitBreakerName());
        return problem;
    }

    @ExceptionHandler(AiAnalysisException.class)
    public ProblemDetail handleAiAnalysisException(AiAnalysisException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "The AI provider could not complete the analysis request.");
        problem.setTitle("AI Analysis Failure");
        problem.setType(AI_ANALYSIS_TYPE);
        return problem;
    }

    @ExceptionHandler(RemediationException.class)
    public ProblemDetail handleRemediationException(RemediationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "Remediation upstream failure: the MCP Server could not execute the mutation.");
        problem.setTitle("Remediation Upstream Failure");
        problem.setType(REMEDIATION_TYPE);
        return problem;
    }
}
