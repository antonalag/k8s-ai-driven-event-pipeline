package com.platform.analyzer.domain.model;

import java.util.List;

/**
 * Immutable value object representing a page of resolved analysis records.
 * No Spring or infrastructure imports — pure domain.
 */
public record AuditLogPage(
        List<AuditLogEntry> content,
        long totalElements,
        int page,
        int totalPages
) {
    public AuditLogPage {
        if (content == null) content = List.of();
        if (totalElements < 0) throw new IllegalArgumentException("totalElements must be >= 0");
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (totalPages < 0) throw new IllegalArgumentException("totalPages must be >= 0");
    }
}
