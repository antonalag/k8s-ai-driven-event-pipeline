package com.platform.analyzer.infrastructure.web;

/**
 * Thrown when audit log query parameters fail validation.
 * Handled by GlobalExceptionHandler to produce RFC 7807 response.
 */
public class AuditLogValidationException extends RuntimeException {

    private final int page;
    private final int size;

    public AuditLogValidationException(int page, int size) {
        super("Invalid pagination parameters: page=" + page + ", size=" + size);
        this.page = page;
        this.size = size;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
