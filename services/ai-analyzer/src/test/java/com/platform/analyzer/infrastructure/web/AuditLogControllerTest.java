package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.model.AnalysisStatus;
import com.platform.analyzer.domain.model.AuditLogEntry;
import com.platform.analyzer.domain.model.AuditLogPage;
import com.platform.analyzer.domain.ports.AuditLogQueryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link AuditLogController}.
 * Uses @WebMvcTest to test the web layer in isolation with mocked port.
 * Requirements: 2.1, 2.4, 2.5, 2.7, 2.8
 */
@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogQueryPort auditLogQueryPort;

    @Test
    @DisplayName("GET /api/v1/analyses/history?page=0&size=10 — 200 with correct structure")
    void happyPathReturns200WithCorrectStructure() throws Exception {
        var entry = new AuditLogEntry(
                Instant.parse("2025-07-06T12:30:00Z"),
                "golden-path-app-5f548d69d9-kjwwg",
                "chaos-validation",
                "CRITICAL_FAILURE",
                AnalysisStatus.REMEDIATED,
                "CrashLoopBackOff due to missing env var DB_URL",
                List.of("kubectl set env deployment/golden-path-app DB_URL=..."),
                "llama3.1:8b"
        );
        var page = new AuditLogPage(List.of(entry), 1L, 0, 1);

        when(auditLogQueryPort.findResolvedAnalyses(0, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/analyses/history")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].resolvedAt").value("2025-07-06T12:30:00Z"))
                .andExpect(jsonPath("$.content[0].podName").value("golden-path-app-5f548d69d9-kjwwg"))
                .andExpect(jsonPath("$.content[0].namespace").value("chaos-validation"))
                .andExpect(jsonPath("$.content[0].verdict").value("CRITICAL_FAILURE"))
                .andExpect(jsonPath("$.content[0].status").value("REMEDIATED"))
                .andExpect(jsonPath("$.content[0].rootCauseAnalysis").value("CrashLoopBackOff due to missing env var DB_URL"))
                .andExpect(jsonPath("$.content[0].recommendedActions[0]").value("kubectl set env deployment/golden-path-app DB_URL=..."))
                .andExpect(jsonPath("$.content[0].modelUsed").value("llama3.1:8b"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(auditLogQueryPort).findResolvedAnalyses(0, 10);
    }

    @Test
    @DisplayName("GET /api/v1/analyses/history (no params) — defaults to page=0, size=10")
    void defaultParametersUsesPage0Size10() throws Exception {
        var page = new AuditLogPage(List.of(), 0L, 0, 0);

        when(auditLogQueryPort.findResolvedAnalyses(0, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/analyses/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));

        verify(auditLogQueryPort).findResolvedAnalyses(0, 10);
    }

    @Test
    @DisplayName("GET /api/v1/analyses/history?page=-1 — 400 RFC 7807 validation error")
    void negativePageReturns400Rfc7807() throws Exception {
        mockMvc.perform(get("/api/v1/analyses/history")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists());

        verifyNoInteractions(auditLogQueryPort);
    }

    @Test
    @DisplayName("GET /api/v1/analyses/history?size=200 — silently caps to 100")
    void sizeCappingUsesMax100() throws Exception {
        var page = new AuditLogPage(List.of(), 0L, 0, 0);

        when(auditLogQueryPort.findResolvedAnalyses(0, 100)).thenReturn(page);

        mockMvc.perform(get("/api/v1/analyses/history")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));

        verify(auditLogQueryPort).findResolvedAnalyses(0, 100);
    }

    @Test
    @DisplayName("GET /api/v1/analyses/history — 200 with empty content array when no results")
    void emptyResultReturns200WithEmptyContent() throws Exception {
        var page = new AuditLogPage(List.of(), 0L, 0, 0);

        when(auditLogQueryPort.findResolvedAnalyses(0, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/analyses/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }
}
