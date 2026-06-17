package com.platform.analyzer.infrastructure.e2e;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.infrastructure.persistence.opensearch.AiAnalysisDocument;
import com.platform.analyzer.infrastructure.persistence.opensearch.OpenSearchAnalysisRepository;
import com.platform.analyzer.infrastructure.persistence.opensearch.SpringDataAiAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying OpenSearch persistence behavior for enriched AI analysis results.
 * Uses Mockito mocks for SpringDataAiAnalysisRepository (no real OpenSearch needed).
 *
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpenSearch Persistence Integration Tests")
class OpenSearchPersistenceIntegrationTest {

    @Mock
    private SpringDataAiAnalysisRepository springDataRepository;

    private OpenSearchAnalysisRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OpenSearchAnalysisRepository(springDataRepository);
    }

    @Test
    @DisplayName("Document ID follows {podName}-{epochMillis} format (Req 6.5)")
    void documentIdFollowsPodNameEpochMillisFormat() {
        // Given
        String podName = "chaos-crashloop-pod";
        AiAnalysis analysis = new AiAnalysis(
                podName,
                "chaos-validation",
                "CrashLoopBackOff",
                "Container exits due to missing ConfigMap key",
                List.of("kubectl create configmap nonexistent-config -n chaos-validation"),
                List.of("describe_pod", "get_events"),
                true
        );

        // When
        AiAnalysisDocument document = AiAnalysisDocument.from(analysis);

        // Then
        assertThat(document.getId()).matches(podName + "-\\d+");
        // Verify the epoch millis part is a valid number
        String epochPart = document.getId().substring(podName.length() + 1);
        assertThat(Long.parseLong(epochPart)).isPositive();
    }

    @Test
    @DisplayName("MCP metadata fields persisted correctly (Req 6.1, 6.2, 6.3)")
    void mcpMetadataFieldsPersistedCorrectly() {
        // Given
        List<String> mcpTools = List.of("describe_pod", "get_events");
        AiAnalysis analysis = new AiAnalysis(
                "chaos-crashloop-pod",
                "chaos-validation",
                "CrashLoopBackOff",
                "Missing ConfigMap reference",
                List.of("kubectl describe pod chaos-crashloop-pod -n chaos-validation"),
                mcpTools,
                true
        );

        // When
        AiAnalysisDocument document = AiAnalysisDocument.from(analysis);

        // Then
        assertThat(document.getMcpToolsUsed()).containsExactly("describe_pod", "get_events");
        assertThat(document.isMcpContextAvailable()).isTrue();
    }

    @Test
    @DisplayName("Retry behavior: save retries 3 times on failure (Req 6.6)")
    void retryBehaviorOnOpenSearchFailure() {
        // Given
        AiAnalysis analysis = new AiAnalysis(
                "chaos-oomkilled-pod",
                "chaos-validation",
                "OOMKilled",
                "Memory limit exceeded",
                List.of("kubectl set resources deployment -n chaos-validation --limits=memory=128Mi"),
                List.of("describe_pod", "get_events", "get_logs"),
                true
        );

        when(springDataRepository.save(any(AiAnalysisDocument.class)))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        repository.save(analysis);

        // Then
        verify(springDataRepository, times(3)).save(any(AiAnalysisDocument.class));
    }

    @Test
    @DisplayName("Non-blocking on failure: no exception propagates after 3 failed attempts (Req 6.6)")
    void nonBlockingOnPersistenceFailure() {
        // Given
        AiAnalysis analysis = new AiAnalysis(
                "chaos-imagepull-pod",
                "chaos-validation",
                "ImagePullBackOff",
                "Image not found in registry",
                List.of("kubectl describe pod chaos-imagepull-pod -n chaos-validation"),
                List.of(),
                false
        );

        when(springDataRepository.save(any(AiAnalysisDocument.class)))
                .thenThrow(new RuntimeException("OpenSearch cluster unavailable"));

        // When / Then — no exception should propagate
        assertThatCode(() -> repository.save(analysis)).doesNotThrowAnyException();

        // Verify all 3 retry attempts were made
        verify(springDataRepository, times(3)).save(any(AiAnalysisDocument.class));
    }

    @Test
    @DisplayName("Labels field initialized as empty HashMap that can be populated (Req 6.4)")
    void labelsFieldInitializedAsEmptyHashMap() {
        // Given
        AiAnalysis analysis = new AiAnalysis(
                "chaos-crashloop-pod",
                "chaos-validation",
                "CrashLoopBackOff",
                "Container crashes on startup",
                List.of("kubectl logs chaos-crashloop-pod -n chaos-validation"),
                List.of("describe_pod", "get_events", "get_logs"),
                true
        );

        // When
        AiAnalysisDocument document = AiAnalysisDocument.from(analysis);

        // Then — labels starts as empty HashMap
        assertThat(document.getLabels()).isNotNull();
        assertThat(document.getLabels()).isEmpty();
        assertThat(document.getLabels()).isInstanceOf(HashMap.class);

        // And can be populated with chaos-type metadata
        Map<String, String> labels = document.getLabels();
        labels.put("chaos-type", "crashloop");
        labels.put("phase", "e2e-validation");

        assertThat(document.getLabels()).containsEntry("chaos-type", "crashloop");
        assertThat(document.getLabels()).containsEntry("phase", "e2e-validation");
    }
}
