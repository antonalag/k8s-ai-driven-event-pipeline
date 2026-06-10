package com.platform.analyzer.config;

import com.platform.analyzer.domain.ports.AiAnalysisQueryPort;
import com.platform.analyzer.infrastructure.web.AiAnalysisQueryController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for CORS configuration using MockMvc.
 * Validates: Requirements 4.1, 4.2, 4.6, 4.7
 */
@WebMvcTest(controllers = AiAnalysisQueryController.class)
@Import(CorsConfig.class)
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiAnalysisQueryPort queryPort;

    @Test
    @DisplayName("Preflight OPTIONS from allowed origin returns correct CORS headers")
    void preflightFromAllowedOriginReturnsCorrectHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/analyses")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    @Test
    @DisplayName("GET from allowed origin includes Access-Control-Allow-Origin header")
    void getFromAllowedOriginIncludesCorsHeader() throws Exception {
        org.mockito.Mockito.when(queryPort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analyses")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    @DisplayName("GET from disallowed origin omits Access-Control-Allow-Origin header")
    void getFromDisallowedOriginOmitsCorsHeader() throws Exception {
        org.mockito.Mockito.when(queryPort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analyses")
                        .header("Origin", "http://evil.com"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Preflight response includes Access-Control-Max-Age: 3600")
    void preflightResponseIncludesMaxAge() throws Exception {
        mockMvc.perform(options("/api/v1/analyses")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    @DisplayName("Preflight response includes Access-Control-Allow-Credentials: true")
    void preflightResponseIncludesAllowCredentials() throws Exception {
        mockMvc.perform(options("/api/v1/analyses")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
