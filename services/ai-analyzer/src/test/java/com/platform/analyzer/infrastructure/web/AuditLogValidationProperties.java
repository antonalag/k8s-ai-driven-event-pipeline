package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.model.AuditLogPage;
import com.platform.analyzer.domain.ports.AuditLogQueryPort;
import net.jqwik.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for audit log parameter validation at the controller level.
 *
 * Validates: Requirements 1.8, 2.4
 */
class AuditLogValidationProperties {

    private final AuditLogQueryPort stubPort = (page, size) ->
            new AuditLogPage(List.of(), 0, page, 0);

    // ─── Property 4: Invalid Parameter Rejection — Negative Page ─────────────────

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 4: Invalid parameter rejection — negative page")
    void negativePagesAreRejected(@ForAll("negativePage") int page) {
        AuditLogController controller = new AuditLogController(stubPort);

        assertThatThrownBy(() -> controller.history(page, 10))
                .isInstanceOf(AuditLogValidationException.class);
    }

    // ─── Property 4: Invalid Parameter Rejection — Zero or Negative Size ─────────

    @Property(tries = 100)
    @Label("Feature: audit-log-history, Property 4: Invalid parameter rejection — zero or negative size")
    void zeroOrNegativeSizeIsRejected(@ForAll("invalidSize") int size) {
        AuditLogController controller = new AuditLogController(stubPort);

        assertThatThrownBy(() -> controller.history(0, size))
                .isInstanceOf(AuditLogValidationException.class);
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Integer> negativePage() {
        return Arbitraries.integers().lessOrEqual(-1);
    }

    @Provide
    Arbitrary<Integer> invalidSize() {
        return Arbitraries.integers().lessOrEqual(0);
    }
}
