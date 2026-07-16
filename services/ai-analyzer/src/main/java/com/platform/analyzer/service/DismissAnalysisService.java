package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.AnalysisLifecycle;
import com.platform.analyzer.domain.model.AnalysisLifecycleEvent;
import com.platform.analyzer.domain.model.DismissalResult;
import com.platform.analyzer.domain.ports.AnalysisAlreadyResolvedException;
import com.platform.analyzer.domain.ports.AnalysisLifecycleRepositoryPort;
import com.platform.analyzer.domain.ports.AnalysisNotFoundException;
import com.platform.analyzer.domain.ports.DismissAnalysisUseCase;
import com.platform.analyzer.domain.ports.LifecycleMessagingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Application-layer service implementing the dismiss analysis use case.
 * Coordinates lifecycle state transition, persistence, event publication,
 * and structured audit logging.
 */
@Service
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
