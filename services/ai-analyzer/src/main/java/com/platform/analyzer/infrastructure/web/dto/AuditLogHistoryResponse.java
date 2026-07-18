package com.platform.analyzer.infrastructure.web.dto;

import com.platform.analyzer.domain.model.AuditLogPage;

import java.util.List;

/**
 * Paginated wrapper response for the audit log history endpoint.
 * Contains a page of {@link HistoryResponseDto} items plus pagination metadata.
 */
public record AuditLogHistoryResponse(
        List<HistoryResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * Factory method mapping an {@link AuditLogPage} domain object to this response DTO.
     *
     * @param auditLogPage  the domain page result
     * @param effectiveSize the effective page size (after capping at 100)
     */
    public static AuditLogHistoryResponse from(AuditLogPage auditLogPage, int effectiveSize) {
        List<HistoryResponseDto> dtos = auditLogPage.content().stream()
                .map(HistoryResponseDto::from)
                .toList();
        return new AuditLogHistoryResponse(
                dtos,
                auditLogPage.page(),
                effectiveSize,
                auditLogPage.totalElements(),
                auditLogPage.totalPages()
        );
    }
}
