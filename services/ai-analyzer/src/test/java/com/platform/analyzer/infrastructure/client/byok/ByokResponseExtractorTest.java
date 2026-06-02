package com.platform.analyzer.infrastructure.client.byok;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analyzer.domain.ports.AiAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByokResponseExtractorTest {

    private ByokResponseExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ByokResponseExtractor(new ObjectMapper());
    }

    @Test
    void extractContent_openAi_validResponse_returnsContent() {
        String body = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "{\\"podName\\":\\"test-pod\\"}"
                      }
                    }
                  ]
                }
                """;

        String result = extractor.extractContent(body, ProviderType.OPENAI_COMPATIBLE);
        assertThat(result).isEqualTo("{\"podName\":\"test-pod\"}");
    }

    @Test
    void extractContent_custom_validResponse_returnsContent() {
        String body = """
                {"response": "{\\"podName\\":\\"test-pod\\"}"}
                """;

        String result = extractor.extractContent(body, ProviderType.CUSTOM);
        assertThat(result).isEqualTo("{\"podName\":\"test-pod\"}");
    }

    @Test
    void extractContent_nullBody_throwsException() {
        assertThatThrownBy(() -> extractor.extractContent(null, ProviderType.OPENAI_COMPATIBLE))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("null or empty response body");
    }

    @Test
    void extractContent_emptyBody_throwsException() {
        assertThatThrownBy(() -> extractor.extractContent("   ", ProviderType.CUSTOM))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("null or empty response body");
    }

    @Test
    void extractContent_openAi_emptyChoices_throwsException() {
        String body = """
                {"choices": []}
                """;

        assertThatThrownBy(() -> extractor.extractContent(body, ProviderType.OPENAI_COMPATIBLE))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("missing 'choices' array or empty");
    }

    @Test
    void extractContent_openAi_missingMessageContent_throwsException() {
        String body = """
                {"choices": [{"message": {"role": "assistant"}}]}
                """;

        assertThatThrownBy(() -> extractor.extractContent(body, ProviderType.OPENAI_COMPATIBLE))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("missing 'choices[0].message.content'");
    }

    @Test
    void extractContent_custom_missingResponseField_throwsException() {
        String body = """
                {"other_field": "value"}
                """;

        assertThatThrownBy(() -> extractor.extractContent(body, ProviderType.CUSTOM))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("missing 'response' field");
    }
}
