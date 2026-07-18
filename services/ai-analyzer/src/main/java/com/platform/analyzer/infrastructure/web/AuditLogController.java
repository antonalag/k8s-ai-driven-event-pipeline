package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.model.AuditLogPage;
import com.platform.analyzer.domain.ports.AuditLogQueryPort;
import com.platform.analyzer.infrastructure.web.dto.AuditLogHistoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the paginated audit log history endpoint.
 * Returns resolved (REMEDIATED/DISMISSED) analyses with pagination metadata.
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AuditLogController {

    private final AuditLogQueryPort auditLogQueryPort;

    public AuditLogController(AuditLogQueryPort auditLogQueryPort) {
        this.auditLogQueryPort = auditLogQueryPort;
    }

    @GetMapping("/history")
    public AuditLogHistoryResponse history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0 || size < 1) {
            throw new AuditLogValidationException(page, size);
        }

        int effectiveSize = Math.min(size, 100);
        AuditLogPage result = auditLogQueryPort.findResolvedAnalyses(page, effectiveSize);

        return AuditLogHistoryResponse.from(result, effectiveSize);
    }
}
