package com.platform.analyzer.infrastructure.client.byok;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.infrastructure.client.byok.dto.CustomProviderRequest;
import com.platform.analyzer.infrastructure.client.byok.dto.OpenAiRequest;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ByokPayloadMapperTest {

    private ByokPayloadMapper mapper;

    private static final KubernetesEvent SAMPLE_EVENT = new KubernetesEvent(
            "crash-pod", "production", PodPhase.Failed, Instant.parse("2025-06-01T10:00:00Z"));

    @BeforeEach
    void setUp() {
        mapper = new ByokPayloadMapper();
    }

    @Test
    void buildRequestBody_openAiCompatible_returnsOpenAiRequest() {
        Object result = mapper.buildRequestBody(
                SAMPLE_EVENT, Collections.emptyList(), "gpt-4o-mini", ProviderType.OPENAI_COMPATIBLE);

        assertThat(result).isInstanceOf(OpenAiRequest.class);
        OpenAiRequest request = (OpenAiRequest) result;
        assertThat(request.model()).isEqualTo("gpt-4o-mini");
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo("system");
        assertThat(request.messages().get(1).role()).isEqualTo("user");
        assertThat(request.messages().get(1).content()).contains("crash-pod");
    }

    @Test
    void buildRequestBody_custom_returnsCustomProviderRequest() {
        Object result = mapper.buildRequestBody(
                SAMPLE_EVENT, Collections.emptyList(), "llama3", ProviderType.CUSTOM);

        assertThat(result).isInstanceOf(CustomProviderRequest.class);
        CustomProviderRequest request = (CustomProviderRequest) result;
        assertThat(request.model()).isEqualTo("llama3");
        assertThat(request.stream()).isFalse();
        assertThat(request.prompt()).contains("crash-pod");
    }

    @Test
    void buildRequestBody_emptyHistory_includesNoRecordsMessage() {
        Object result = mapper.buildRequestBody(
                SAMPLE_EVENT, Collections.emptyList(), "gpt-4o-mini", ProviderType.OPENAI_COMPATIBLE);

        OpenAiRequest request = (OpenAiRequest) result;
        assertThat(request.messages().get(0).content())
                .contains("No previous analysis records found for this pod.");
    }

    @Test
    void buildRequestBody_nullHistory_includesNoRecordsMessage() {
        Object result = mapper.buildRequestBody(
                SAMPLE_EVENT, null, "gpt-4o-mini", ProviderType.OPENAI_COMPATIBLE);

        OpenAiRequest request = (OpenAiRequest) result;
        assertThat(request.messages().get(0).content())
                .contains("No previous analysis records found for this pod.");
    }

    @Test
    void buildRequestBody_withHistory_includesVerdicts() {
        List<AiAnalysis> history = List.of(
                new AiAnalysis("crash-pod", "production", "CRITICAL_FAILURE",
                        "OOMKilled", List.of("Increase memory")),
                new AiAnalysis("crash-pod", "production", "TRANSIENT_ISSUE",
                        "Scheduling delay", List.of("Check node capacity"))
        );

        Object result = mapper.buildRequestBody(
                SAMPLE_EVENT, history, "gpt-4o-mini", ProviderType.OPENAI_COMPATIBLE);

        OpenAiRequest request = (OpenAiRequest) result;
        String systemContent = request.messages().get(0).content();
        assertThat(systemContent).contains("CRITICAL_FAILURE");
        assertThat(systemContent).contains("OOMKilled");
        assertThat(systemContent).contains("TRANSIENT_ISSUE");
        assertThat(systemContent).contains("Scheduling delay");
    }
}
