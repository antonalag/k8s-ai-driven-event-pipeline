package com.platform.analyzer.domain.service;

import com.platform.analyzer.domain.model.entity.AnalysisLifecycle;
import com.platform.analyzer.domain.model.event.AnalysisLifecycleEvent;
import com.platform.analyzer.domain.model.valueobject.DismissalResult;
import com.platform.analyzer.domain.exception.AnalysisAlreadyResolvedException;
import com.platform.analyzer.domain.port.outbound.AnalysisLifecycleRepositoryPort;
import com.platform.analyzer.domain.exception.AnalysisNotFoundException;
import com.platform.analyzer.domain.port.inbound.DismissAnalysisUseCase;
import com.platform.analyzer.domain.port.outbound.LifecycleMessagingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

/**
 * Application-layer service implementing the dismiss analysis use case.
 * Coordinates lifecycle state transition, persistence, event publication,
 * and structured audit logging.
 */
public class DismissAnalysisService implements DismissAnalysisUseCase {

    private static final Logger log = LoggerFactory.getLogger(DismissAnalysisService.class);

    private final AnalysisLifecycleRepositoryPort lifecycleRepository;
    private final LifecycleMessagingPort messagingPort;

    public DismissAnalysisService(AnalysisLifecycleRepositoryPort lifecycleRepository,
                                  LifecycleMessagingPort messagingPort) {
        this.lifecycleRepository = lifecycleRepository;
        this.messagingPort = messagingPort;
    }

    @Override
    public DismissalResult dismiss(String analysisId, String reason) {
        AnalysisLifecycle lifecycle = lifecycleRepository.findById(analysisId)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisId));

        if (lifecycle.isResolved()) {
            throw new AnalysisAlreadyResolvedException(
                    analysisId, lifecycle.getStatus().name());
        }

        lifecycle.dismiss(reason, LocalDateTime.now());
        lifecycleRepository.save(lifecycle);

        AnalysisLifecycleEvent event = AnalysisLifecycleEvent.dismissed(lifecycle);
        messagingPort.publishLifecycleEvent(event);

        log.info("[DISMISS] Analysis '{}' dismissed. Reason: '{}'",
                analysisId, lifecycle.getResolutionReason());

        return new DismissalResult(analysisId, lifecycle.getStatus());
    }
}
