package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.model.DismissalResult;
import com.platform.analyzer.domain.ports.DismissAnalysisUseCase;
import com.platform.analyzer.infrastructure.web.dto.DismissRequest;
import com.platform.analyzer.infrastructure.web.dto.DismissResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the analysis dismissal endpoint.
 * Delegates to {@link DismissAnalysisUseCase} and returns structured JSON on success.
 * Error responses (404, 409) are handled by {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisDismissalController {

    private final DismissAnalysisUseCase dismissUseCase;

    public AnalysisDismissalController(DismissAnalysisUseCase dismissUseCase) {
        this.dismissUseCase = dismissUseCase;
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<DismissResponse> dismiss(
            @PathVariable String id,
            @RequestBody(required = false) DismissRequest request) {

        String reason = (request != null) ? request.reason() : null;
        DismissalResult result = dismissUseCase.dismiss(id, reason);

        return ResponseEntity.ok(new DismissResponse(
                result.analysisId(),
                result.newStatus().name()
        ));
    }
}
