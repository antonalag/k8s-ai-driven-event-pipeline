package com.platform.analyzer.infrastructure.adapter.outbound.resilience;

import com.platform.analyzer.domain.model.command.FixImageCommand;
import com.platform.analyzer.domain.model.valueobject.RemediationResult;
import com.platform.analyzer.domain.model.command.RestartCommand;
import com.platform.analyzer.domain.model.command.ScaleCommand;
import com.platform.analyzer.domain.port.outbound.RemediationPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import java.time.Instant;

/**
 * Decorator that wraps a {@link RemediationPort} with the mutation circuit breaker.
 * When the breaker is OPEN, operations return a {@link RemediationResult.Failure}
 * with errorCode "CIRCUIT_OPEN" instead of throwing.
 */
public class ResilientRemediationPortAdapter implements RemediationPort {

    private final RemediationPort delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientRemediationPortAdapter(RemediationPort delegate, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public RemediationResult restartDeployment(RestartCommand command) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.restartDeployment(command));
        } catch (CallNotPermittedException ex) {
            return circuitOpenFailure("restart_deployment");
        }
    }

    @Override
    public RemediationResult scaleDeployment(ScaleCommand command) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.scaleDeployment(command));
        } catch (CallNotPermittedException ex) {
            return circuitOpenFailure("scale_deployment");
        }
    }

    @Override
    public RemediationResult fixContainerImage(FixImageCommand command) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.fixContainerImage(command));
        } catch (CallNotPermittedException ex) {
            return circuitOpenFailure("fix_container_image");
        }
    }

    private RemediationResult.Failure circuitOpenFailure(String action) {
        return new RemediationResult.Failure(
                action,
                "CIRCUIT_OPEN",
                "Mutation circuit breaker is open — cluster write operations temporarily suspended",
                Instant.now()
        );
    }
}
