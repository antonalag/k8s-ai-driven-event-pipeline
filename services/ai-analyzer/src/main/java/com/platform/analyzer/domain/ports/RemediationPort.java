package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.FixImageCommand;
import com.platform.analyzer.domain.model.RemediationResult;
import com.platform.analyzer.domain.model.RestartCommand;
import com.platform.analyzer.domain.model.ScaleCommand;

/**
 * Port for dispatching remediation commands to the Kubernetes cluster via write-back tools.
 * Implementations (MCP Server write-back adapters) reside in infrastructure/.
 *
 * <p>Each method accepts a typed command record and returns a sealed {@link RemediationResult}
 * that is either a {@link RemediationResult.Success} or {@link RemediationResult.Failure}.
 */
public interface RemediationPort {

    RemediationResult restartDeployment(RestartCommand command);

    RemediationResult scaleDeployment(ScaleCommand command);

    RemediationResult fixContainerImage(FixImageCommand command);
}
