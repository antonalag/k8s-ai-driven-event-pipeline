package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.model.AnalysisStatus;
import com.platform.analyzer.domain.model.DismissalResult;
import com.platform.analyzer.domain.ports.AnalysisAlreadyResolvedException;
import com.platform.analyzer.domain.ports.AnalysisNotFoundException;
import com.platform.analyzer.domain.ports.DismissAnalysisUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link AnalysisDismissalController}.
 * Uses @WebMvcTest to test the web layer in isolation with mocked use case.
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */
@WebMvcTest(AnalysisDismissalController.class)
class AnalysisDismissalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DismissAnalysisUseCase dismissUseCase;

    @Test
    @DisplayName("POST /api/v1/analyses/{id}/dismiss — 200 with reason body")
    void dismissWithReasonReturns200() throws Exception {
        when(dismissUseCase.dismiss("doc-123", "False positive"))
                .thenReturn(new DismissalResult("doc-123", AnalysisStatus.DISMISSED));

        mockMvc.perform(post("/api/v1/analyses/doc-123/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"False positive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("doc-123"))
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    @DisplayName("POST /api/v1/analyses/{id}/dismiss — 200 without body (optional)")
    void dismissWithoutBodyReturns200() throws Exception {
        when(dismissUseCase.dismiss("doc-456", null))
                .thenReturn(new DismissalResult("doc-456", AnalysisStatus.DISMISSED));

        mockMvc.perform(post("/api/v1/analyses/doc-456/dismiss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("doc-456"))
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    @DisplayName("POST /api/v1/analyses/{id}/dismiss — 404 when analysis not found")
    void dismissNotFoundReturns404() throws Exception {
        when(dismissUseCase.dismiss("nonexistent", null))
                .thenThrow(new AnalysisNotFoundException("nonexistent"));

        mockMvc.perform(post("/api/v1/analyses/nonexistent/dismiss"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Analysis Not Found"))
                .andExpect(jsonPath("$.type").value("urn:problem-type:analysis-not-found"))
                .andExpect(jsonPath("$.analysisId").value("nonexistent"));
    }

    @Test
    @DisplayName("POST /api/v1/analyses/{id}/dismiss — 409 when already dismissed")
    void dismissAlreadyDismissedReturns409() throws Exception {
        when(dismissUseCase.dismiss("doc-789", "try again"))
                .thenThrow(new AnalysisAlreadyResolvedException("doc-789", "DISMISSED"));

        mockMvc.perform(post("/api/v1/analyses/doc-789/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"try again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Analysis Already Resolved"))
                .andExpect(jsonPath("$.type").value("urn:problem-type:analysis-already-resolved"))
                .andExpect(jsonPath("$.analysisId").value("doc-789"))
                .andExpect(jsonPath("$.currentStatus").value("DISMISSED"));
    }

    @Test
    @DisplayName("POST /api/v1/analyses/{id}/dismiss — 409 when already remediated")
    void dismissAlreadyRemediatedReturns409() throws Exception {
        when(dismissUseCase.dismiss("doc-abc", null))
                .thenThrow(new AnalysisAlreadyResolvedException("doc-abc", "REMEDIATED"));

        mockMvc.perform(post("/api/v1/analyses/doc-abc/dismiss"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("REMEDIATED"));
    }
}
