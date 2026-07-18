package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.model.AuditLogPage;
import com.platform.analyzer.domain.ports.AuditLogQueryPort;
import com.platform.analyzer.infrastructure.web.dto.AuditLogHistoryResponse;
import net.jqwik.api.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for size capping logic in the AuditLogController.
 *
 * Validates: Requirements 2.5
 */
class AuditLogControllerProperties {

    // ─── Property 5: Size Capping ────────────────────────────────────────────────

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 5: Size capping — size > 100 capped to 100")
    void sizeGreaterThan100IsCappedTo100(@ForAll("sizeAbove100") int requestedSize) {
        AtomicInteger capturedSize = new AtomicInteger(-1);

        AuditLogQueryPort spyPort = (page, size) -> {
            capturedSize.set(size);
            return new AuditLogPage(List.of(), 0, page, 0);
        };

        AuditLogController controller = new AuditLogController(spyPort);
        AuditLogHistoryResponse response = controller.history(0, requestedSize);

        assertThat(capturedSize.get()).isEqualTo(100);
        assertThat(response.size()).isEqualTo(100);
    }

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 5: Size capping — size <= 100 passed as-is")
    void sizeAtOrBelow100IsPassedUnchanged(@ForAll("validSize") int requestedSize) {
        AtomicInteger capturedSize = new AtomicInteger(-1);

        AuditLogQueryPort spyPort = (page, size) -> {
            capturedSize.set(size);
            return new AuditLogPage(List.of(), 0, page, 0);
        };

        AuditLogController controller = new AuditLogController(spyPort);
        AuditLogHistoryResponse response = controller.history(0, requestedSize);

        assertThat(capturedSize.get()).isEqualTo(requestedSize);
        assertThat(response.size()).isEqualTo(requestedSize);
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Integer> sizeAbove100() {
        return Arbitraries.integers().between(101, 10_000);
    }

    @Provide
    Arbitrary<Integer> validSize() {
        return Arbitraries.integers().between(1, 100);
    }
}
