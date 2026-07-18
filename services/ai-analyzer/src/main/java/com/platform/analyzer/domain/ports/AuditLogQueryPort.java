package com.platform.analyzer.domain.ports;

import com.platform.analyzer.domain.model.AuditLogPage;

/**
 * Read-only port for querying resolved (REMEDIATED/DISMISSED) analysis history.
 * Separated from AiAnalysisQueryPort following CQRS-lite.
 */
public interface AuditLogQueryPort {

    /**
     * Returns a paginated list of resolved analyses sorted by resolvedAt DESC.
     *
     * @param page zero-indexed page number (must be >= 0)
     * @param size items per page (must be 1..100)
     * @return paginated result with content, metadata
     * @throws IllegalArgumentException if page < 0 or size out of [1, 100]
     */
    AuditLogPage findResolvedAnalyses(int page, int size);
}
