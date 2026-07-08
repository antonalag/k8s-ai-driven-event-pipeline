package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.FixImageCommand;
import com.platform.analyzer.domain.model.RemediationResult;
import com.platform.analyzer.domain.model.RestartCommand;
import com.platform.analyzer.domain.model.ScaleCommand;
import com.platform.analyzer.domain.ports.AiAnalysisRepositoryPort;
import com.platform.analyzer.domain.ports.RemediationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application-layer orchestrator for remediation requests.
 * Coordinates command construction, port dispatch, and structured audit logging.
 * On successful remediation, persists a HEALTHY verdict to close the diagnostic loop.
 */
@Service
public class RemediationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RemediationOrchestrator.class);

    private final RemediationPort remediationPort;
    private final AiAnalysisRepositoryPort analysisRepository;

    public RemediationOrchestrator(RemediationPort remediationPort,
                                   AiAnalysisRepositoryPort analysisRepository) {
        this.remediationPort = remediationPort;
        this.analysisRepository = analysisRepository;
    }

    /**
     * Executes a remediation action via the port.
     *
     * @param action the tool name (restart_deployment, scale_deployment, fix_container_image)
     * @param correlationId UUID linking to the original AiAnalysis failure
     * @param deploymentName target deployment
     * @param namespace target namespace
     * @param replicas desired replicas (nullable, only for scale_deployment)
     * @param containerName container to patch (nullable, only for fix_container_image)
     * @param correctImage image to apply (nullable, only for fix_container_image)
     * @return RemediationResult (Success or Failure)
     */
    public RemediationResult execute(String action,
                                     UUID correlationId,
                                     String deploymentName,
                                     String namespace,
                                     Integer replicas,
                                     String containerName,
                                     String correctImage) {

        long startTime = System.currentTimeMillis();

        RemediationResult result = dispatch(action, correlationId, deploymentName, namespace,
                replicas, containerName, correctImage);

        long durationMs = System.currentTimeMillis() - startTime;

        if (result instanceof RemediationResult.Success) {
            logSuccess(correlationId, action, deploymentName, namespace, durationMs);
            persistHealthyVerdict(deploymentName, namespace, action);
        } else if (result instanceof RemediationResult.Failure failure) {
            logFailure(correlationId, action, failure.errorCode(), failure.errorMessage(), durationMs);
        }

        return result;
    }

    /**
     * Routes the request to the appropriate RemediationPort method based on action name.
     */
    private RemediationResult dispatch(String action,
                                       UUID correlationId,
                                       String deploymentName,
                                       String namespace,
                                       Integer replicas,
                                       String containerName,
                                       String correctImage) {
        return switch (action) {
            case "restart_deployment" -> remediationPort.restartDeployment(
                    new RestartCommand(correlationId, deploymentName, namespace));

            case "scale_deployment" -> {
                if (replicas == null) {
                    yield new RemediationResult.Failure(action, "INVALID_PARAMS",
                            "replicas is required for scale_deployment", Instant.now());
                }
                yield remediationPort.scaleDeployment(
                        new ScaleCommand(correlationId, deploymentName, namespace, replicas));
            }

            case "fix_container_image" -> {
                if (containerName == null || containerName.isBlank()) {
                    yield new RemediationResult.Failure(action, "INVALID_PARAMS",
                            "containerName is required for fix_container_image", Instant.now());
                }
                if (correctImage == null || correctImage.isBlank()) {
                    yield new RemediationResult.Failure(action, "INVALID_PARAMS",
                            "correctImage is required for fix_container_image", Instant.now());
                }
                yield remediationPort.fixContainerImage(
                        new FixImageCommand(correlationId, deploymentName, namespace, containerName, correctImage));
            }

            default -> new RemediationResult.Failure(action, "UNSUPPORTED_ACTION",
                    "Action '" + action + "' is not a supported remediation action", Instant.now());
        };
    }

    private void logSuccess(UUID correlationId, String action, String deployment, String namespace, long durationMs) {
        log.info("""
                {"event":"remediation_executed","correlationId":"{}","action":"{}","deployment":"{}","namespace":"{}","status":"success","durationMs":{}}""",
                correlationId, action, deployment, namespace, durationMs);
    }

    private void logFailure(UUID correlationId, String action, String errorCode, String errorMessage, long durationMs) {
        log.warn("""
                {"event":"remediation_failed","correlationId":"{}","action":"{}","errorCode":"{}","errorMessage":"{}","durationMs":{}}""",
                correlationId, action, errorCode, errorMessage, durationMs);
    }

    /**
     * Persists a HEALTHY verdict for the deployment after successful remediation.
     * Uses the deploymentName as a synthetic podName prefix that the query controller
     * groups with the original pod analyses via extractDeploymentPrefix.
     */
    private void persistHealthyVerdict(String deploymentName, String namespace, String action) {
        try {
            // Synthetic podName following K8s naming: deployment-remediated-resolved
            String syntheticPodName = deploymentName + "-remediated-resolved";

            AiAnalysis healthy = new AiAnalysis(
                    syntheticPodName,
                    namespace,
                    "HEALTHY",
                    "Remediated via " + action + ". Pod recovered.",
                    List.of()
            );
            analysisRepository.save(healthy);

            log.info("[REMEDIATION] Persisted HEALTHY verdict for deployment '{}'", deploymentName);
        } catch (Exception e) {
            log.warn("[REMEDIATION] Failed to persist HEALTHY verdict for deployment '{}': {}",
                    deploymentName, e.getMessage());
        }
    }
}
