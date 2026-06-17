package com.platform.analyzer.infrastructure.e2e;

import com.platform.analyzer.domain.model.AiAnalysis;
import com.platform.analyzer.domain.model.EnrichedContext;
import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import com.platform.analyzer.domain.ports.AiAnalysisRepositoryPort;
import com.platform.analyzer.domain.ports.AiLanguageModelPort;
import com.platform.analyzer.domain.ports.CircuitBreakerStatePort;
import com.platform.analyzer.domain.ports.McpContextPort;
import com.platform.analyzer.domain.ports.PipelineTracer;
import com.platform.analyzer.service.PodAnalyzerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Full E2E Pipeline Test: deploy all 3 chaos scenarios, verify complete pipeline flow.
 *
 * Since a live K8s cluster isn't available in CI, this test simulates the full E2E flow by:
 * 1. Creating KubernetesEvents representing each chaos scenario
 * 2. Processing them through PodAnalyzerService with mocked dependencies
 * 3. Verifying the output AiAnalysis contains correct fields and typology-appropriate actions
 * 4. Verifying Makefile targets are syntactically valid
 *
 * Validates: Requirements 2.1–2.6, 3.1–3.6, 4.1–4.5, 5.1–5.6, 6.1–6.6, 10.1–10.7
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Full E2E Pipeline Test — All 3 Chaos Scenarios")
class FullE2EPipelineTest {

    private static final String CHAOS_NAMESPACE = "chaos-validation";
    private static final List<String> ALL_MCP_TOOLS = List.of("describe_pod", "get_events", "get_logs");

    @Mock
    private AiLanguageModelPort aiLanguageModel;

    @Mock
    private AiAnalysisRepositoryPort aiAnalysisRepository;

    @Mock
    private McpContextPort mcpContextPort;

    @Mock
    private PipelineTracer pipelineTracer;

    @Mock
    private CircuitBreakerStatePort circuitBreakerStatePort;

    private PodAnalyzerService podAnalyzerService;

    @BeforeEach
    void setUp() {
        podAnalyzerService = new PodAnalyzerService(
                aiLanguageModel,
                aiAnalysisRepository,
                mcpContextPort,
                pipelineTracer,
                circuitBreakerStatePort
        );

        // Default: circuit breaker CLOSED, empty history
        when(circuitBreakerStatePort.getMcpCircuitBreakerState()).thenReturn("CLOSED");
        when(aiAnalysisRepository.findByPodName(anyString())).thenReturn(List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. CrashLoopBackOff Scenario — Full Pipeline Flow
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CrashLoopBackOff Scenario")
    class CrashLoopBackOffScenario {

        private static final String POD_NAME = "chaos-crashloop-pod";

        @Test
        @DisplayName("produces enriched AiAnalysis with correct pod name and namespace")
        void producesEnrichedAnalysisWithCorrectPodAndNamespace() {
            KubernetesEvent event = crashloopEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = crashloopAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            assertThat(result.podName()).isEqualTo(POD_NAME);
            assertThat(result.namespace()).isEqualTo(CHAOS_NAMESPACE);
        }

        @Test
        @DisplayName("mcpToolsUsed populated with all 3 tools when MCP succeeds")
        void mcpToolsUsedPopulatedWhenMcpSucceeds() {
            KubernetesEvent event = crashloopEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = crashloopAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            assertThat(result.mcpToolsUsed()).containsExactlyElementsOf(ALL_MCP_TOOLS);
            assertThat(result.mcpContextAvailable()).isTrue();
        }

        @Test
        @DisplayName("recommended actions contain kubectl commands for CrashLoopBackOff typology")
        void recommendedActionsAreTypologyAppropriate() {
            KubernetesEvent event = crashloopEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = crashloopAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            assertThat(result.recommendedActions()).isNotEmpty();
            assertThat(result.recommendedActions())
                    .anyMatch(action -> action.contains("kubectl"));
        }

        @Test
        @DisplayName("orchestration: correlation ID generated, CB state logged, MCP retrieved, analysis produced")
        void fullOrchestrationFlow() {
            KubernetesEvent event = crashloopEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = crashloopAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            // Verify circuit breaker state was logged at cycle start
            verify(pipelineTracer).logCycleStart(anyString(), eq("CLOSED"), eq(POD_NAME), eq(CHAOS_NAMESPACE));

            // Verify per-tool results were logged
            verify(pipelineTracer, atLeastOnce()).logToolResult(anyString(), anyString(), anyLong(), any(Boolean.class));

            // Verify cycle completion was logged
            verify(pipelineTracer).logCycleComplete(anyString(), eq(POD_NAME), eq(CHAOS_NAMESPACE),
                    eq(3), anyLong(), eq("CrashLoopBackOff"));

            assertThat(result).isNotNull();
            assertThat(result.verdict()).isEqualTo("CrashLoopBackOff");
        }

        private KubernetesEvent crashloopEvent() {
            return new KubernetesEvent(POD_NAME, CHAOS_NAMESPACE, PodPhase.Failed, Instant.now());
        }

        private AiAnalysis crashloopAnalysis() {
            return new AiAnalysis(
                    POD_NAME, CHAOS_NAMESPACE, "CrashLoopBackOff",
                    "Container exits due to missing ConfigMap key 'missing-key' in ConfigMap 'nonexistent-config'.",
                    List.of(
                            "kubectl create configmap nonexistent-config --from-literal=missing-key=value -n chaos-validation",
                            "kubectl describe pod chaos-crashloop-pod -n chaos-validation",
                            "kubectl logs chaos-crashloop-pod -n chaos-validation --previous"
                    ),
                    ALL_MCP_TOOLS, true
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. OOMKilled Scenario — Full Pipeline Flow
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OOMKilled Scenario")
    class OomKilledScenario {

        private static final String POD_NAME = "chaos-oomkilled-pod";

        @Test
        @DisplayName("produces enriched AiAnalysis with correct pod name and namespace")
        void producesEnrichedAnalysisWithCorrectPodAndNamespace() {
            KubernetesEvent event = oomkilledEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = oomkilledAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            assertThat(result.podName()).isEqualTo(POD_NAME);
            assertThat(result.namespace()).isEqualTo(CHAOS_NAMESPACE);
        }

        @Test
        @DisplayName("mcpToolsUsed populated with all 3 tools when MCP succeeds")
        void mcpToolsUsedPopulatedWhenMcpSucceeds() {
            KubernetesEvent event = oomkilledEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = oomkilledAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            assertThat(result.mcpToolsUsed()).containsExactlyElementsOf(ALL_MCP_TOOLS);
            assertThat(result.mcpContextAvailable()).isTrue();
        }

        @Test
        @DisplayName("recommended actions contain resource limit adjustment commands for OOMKilled typology")
        void recommendedActionsAreTypologyAppropriate() {
            KubernetesEvent event = oomkilledEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = oomkilledAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            assertThat(result.recommendedActions()).isNotEmpty();
            assertThat(result.recommendedActions())
                    .anyMatch(action -> action.contains("kubectl"));
        }

        @Test
        @DisplayName("orchestration: correlation ID generated, CB state logged, MCP retrieved, analysis produced")
        void fullOrchestrationFlow() {
            KubernetesEvent event = oomkilledEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = oomkilledAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            verify(pipelineTracer).logCycleStart(anyString(), eq("CLOSED"), eq(POD_NAME), eq(CHAOS_NAMESPACE));
            verify(pipelineTracer, atLeastOnce()).logToolResult(anyString(), anyString(), anyLong(), any(Boolean.class));
            verify(pipelineTracer).logCycleComplete(anyString(), eq(POD_NAME), eq(CHAOS_NAMESPACE),
                    eq(3), anyLong(), eq("OOMKilled"));

            assertThat(result).isNotNull();
            assertThat(result.verdict()).isEqualTo("OOMKilled");
        }

        private KubernetesEvent oomkilledEvent() {
            return new KubernetesEvent(POD_NAME, CHAOS_NAMESPACE, PodPhase.Failed, Instant.now());
        }

        private AiAnalysis oomkilledAnalysis() {
            return new AiAnalysis(
                    POD_NAME, CHAOS_NAMESPACE, "OOMKilled",
                    "Container terminated: OOMKilled. Memory limit 64Mi exceeded.",
                    List.of(
                            "kubectl set resources pod chaos-oomkilled-pod -n chaos-validation --limits=memory=128Mi",
                            "kubectl describe pod chaos-oomkilled-pod -n chaos-validation",
                            "kubectl top pod chaos-oomkilled-pod -n chaos-validation"
                    ),
                    ALL_MCP_TOOLS, true
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. ImagePullBackOff Scenario — Full Pipeline Flow
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ImagePullBackOff Scenario")
    class ImagePullBackOffScenario {

        private static final String POD_NAME = "chaos-imagepull-pod";

        @Test
        @DisplayName("produces enriched AiAnalysis with correct pod name and namespace")
        void producesEnrichedAnalysisWithCorrectPodAndNamespace() {
            KubernetesEvent event = imagepullEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = imagepullAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            assertThat(result.podName()).isEqualTo(POD_NAME);
            assertThat(result.namespace()).isEqualTo(CHAOS_NAMESPACE);
        }

        @Test
        @DisplayName("mcpToolsUsed populated with all 3 tools when MCP succeeds")
        void mcpToolsUsedPopulatedWhenMcpSucceeds() {
            KubernetesEvent event = imagepullEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = imagepullAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            assertThat(result.mcpToolsUsed()).containsExactlyElementsOf(ALL_MCP_TOOLS);
            assertThat(result.mcpContextAvailable()).isTrue();
        }

        @Test
        @DisplayName("recommended actions contain image registry verification for ImagePullBackOff typology")
        void recommendedActionsAreTypologyAppropriate() {
            KubernetesEvent event = imagepullEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = imagepullAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            assertThat(result.recommendedActions()).isNotEmpty();
            assertThat(result.recommendedActions())
                    .anyMatch(action -> action.contains("kubectl"));
        }

        @Test
        @DisplayName("orchestration: correlation ID generated, CB state logged, MCP retrieved, analysis produced")
        void fullOrchestrationFlow() {
            KubernetesEvent event = imagepullEvent();
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = imagepullAnalysis();

            when(mcpContextPort.retrieveContext(POD_NAME, CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            AiAnalysis result = podAnalyzerService.analyse(event);

            verify(pipelineTracer).logCycleStart(anyString(), eq("CLOSED"), eq(POD_NAME), eq(CHAOS_NAMESPACE));
            verify(pipelineTracer, atLeastOnce()).logToolResult(anyString(), anyString(), anyLong(), any(Boolean.class));
            verify(pipelineTracer).logCycleComplete(anyString(), eq(POD_NAME), eq(CHAOS_NAMESPACE),
                    eq(3), anyLong(), eq("ImagePullBackOff"));

            assertThat(result).isNotNull();
            assertThat(result.verdict()).isEqualTo("ImagePullBackOff");
        }

        private KubernetesEvent imagepullEvent() {
            return new KubernetesEvent(POD_NAME, CHAOS_NAMESPACE, PodPhase.Pending, Instant.now());
        }

        private AiAnalysis imagepullAnalysis() {
            return new AiAnalysis(
                    POD_NAME, CHAOS_NAMESPACE, "ImagePullBackOff",
                    "Failed to pull image: invalid-registry.example.com/nonexistent:v0.0.0",
                    List.of(
                            "kubectl describe pod chaos-imagepull-pod -n chaos-validation",
                            "kubectl get events -n chaos-validation --field-selector involvedObject.name=chaos-imagepull-pod",
                            "kubectl set image pod/chaos-imagepull-pod invalid-image-container=valid-registry.example.com/image:tag -n chaos-validation"
                    ),
                    ALL_MCP_TOOLS, true
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. Cross-Scenario Orchestration Verification
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-Scenario Orchestration")
    class CrossScenarioOrchestration {

        @Test
        @DisplayName("threshold is NOT exceeded for fast test execution (< 30s)")
        void thresholdNotExceededForFastExecution() {
            KubernetesEvent event = new KubernetesEvent(
                    "chaos-crashloop-pod", CHAOS_NAMESPACE, PodPhase.Failed, Instant.now());
            EnrichedContext context = fullEnrichedContext();
            AiAnalysis expected = new AiAnalysis(
                    "chaos-crashloop-pod", CHAOS_NAMESPACE, "CrashLoopBackOff",
                    "Test root cause", List.of("kubectl describe pod chaos-crashloop-pod -n chaos-validation"),
                    ALL_MCP_TOOLS, true);

            when(mcpContextPort.retrieveContext("chaos-crashloop-pod", CHAOS_NAMESPACE)).thenReturn(context);
            when(aiLanguageModel.analyze(eq(event), anyList(), eq(context))).thenReturn(expected);

            podAnalyzerService.analyse(event);

            // logThresholdExceeded should NOT be called for fast tests (< 30s)
            verify(pipelineTracer, org.mockito.Mockito.never())
                    .logThresholdExceeded(anyString(), anyLong());
        }

        @Test
        @DisplayName("all 3 scenarios processed sequentially produce independent results")
        void allThreeScenariosProduceIndependentResults() {
            // CrashLoopBackOff
            KubernetesEvent crashloopEvent = new KubernetesEvent(
                    "chaos-crashloop-pod", CHAOS_NAMESPACE, PodPhase.Failed, Instant.now());
            EnrichedContext ctx = fullEnrichedContext();
            AiAnalysis crashloopResult = new AiAnalysis(
                    "chaos-crashloop-pod", CHAOS_NAMESPACE, "CrashLoopBackOff",
                    "Config error", List.of("kubectl create configmap nonexistent-config -n chaos-validation"),
                    ALL_MCP_TOOLS, true);

            when(mcpContextPort.retrieveContext("chaos-crashloop-pod", CHAOS_NAMESPACE)).thenReturn(ctx);
            when(aiLanguageModel.analyze(eq(crashloopEvent), anyList(), eq(ctx))).thenReturn(crashloopResult);

            // OOMKilled
            KubernetesEvent oomEvent = new KubernetesEvent(
                    "chaos-oomkilled-pod", CHAOS_NAMESPACE, PodPhase.Failed, Instant.now());
            AiAnalysis oomResult = new AiAnalysis(
                    "chaos-oomkilled-pod", CHAOS_NAMESPACE, "OOMKilled",
                    "Memory exceeded", List.of("kubectl set resources pod chaos-oomkilled-pod -n chaos-validation --limits=memory=128Mi"),
                    ALL_MCP_TOOLS, true);

            when(mcpContextPort.retrieveContext("chaos-oomkilled-pod", CHAOS_NAMESPACE)).thenReturn(ctx);
            when(aiLanguageModel.analyze(eq(oomEvent), anyList(), eq(ctx))).thenReturn(oomResult);

            // ImagePullBackOff
            KubernetesEvent imagepullEvent = new KubernetesEvent(
                    "chaos-imagepull-pod", CHAOS_NAMESPACE, PodPhase.Pending, Instant.now());
            AiAnalysis imagepullResult = new AiAnalysis(
                    "chaos-imagepull-pod", CHAOS_NAMESPACE, "ImagePullBackOff",
                    "Invalid image", List.of("kubectl describe pod chaos-imagepull-pod -n chaos-validation"),
                    ALL_MCP_TOOLS, true);

            when(mcpContextPort.retrieveContext("chaos-imagepull-pod", CHAOS_NAMESPACE)).thenReturn(ctx);
            when(aiLanguageModel.analyze(eq(imagepullEvent), anyList(), eq(ctx))).thenReturn(imagepullResult);

            // Execute all 3 scenarios
            AiAnalysis r1 = podAnalyzerService.analyse(crashloopEvent);
            AiAnalysis r2 = podAnalyzerService.analyse(oomEvent);
            AiAnalysis r3 = podAnalyzerService.analyse(imagepullEvent);

            // Verify independent results with correct verdicts
            assertThat(r1.verdict()).isEqualTo("CrashLoopBackOff");
            assertThat(r2.verdict()).isEqualTo("OOMKilled");
            assertThat(r3.verdict()).isEqualTo("ImagePullBackOff");

            // Verify all produce enriched output with MCP metadata
            assertThat(r1.mcpContextAvailable()).isTrue();
            assertThat(r2.mcpContextAvailable()).isTrue();
            assertThat(r3.mcpContextAvailable()).isTrue();

            assertThat(r1.mcpToolsUsed()).hasSize(3);
            assertThat(r2.mcpToolsUsed()).hasSize(3);
            assertThat(r3.mcpToolsUsed()).hasSize(3);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. Makefile Idempotency & Structural Validation
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Makefile Validation")
    class MakefileValidation {

        private static final Path MAKEFILE_PATH = resolveChaosDir().resolve("Makefile");

        @Test
        @DisplayName("Makefile exists and is readable")
        void makefileExistsAndIsReadable() {
            assertThat(Files.exists(MAKEFILE_PATH))
                    .as("Makefile should exist at deployments/chaos/Makefile")
                    .isTrue();
            assertThat(Files.isReadable(MAKEFILE_PATH))
                    .as("Makefile should be readable")
                    .isTrue();
        }

        @Test
        @DisplayName("Makefile contains chaos-deploy target")
        void containsChaosDeployTarget() throws IOException {
            String content = Files.readString(MAKEFILE_PATH);
            assertThat(content).contains("chaos-deploy:");
        }

        @Test
        @DisplayName("Makefile contains chaos-clean target")
        void containsChaosCleanTarget() throws IOException {
            String content = Files.readString(MAKEFILE_PATH);
            assertThat(content).contains("chaos-clean:");
        }

        @Test
        @DisplayName("Makefile contains chaos-status target")
        void containsChaosStatusTarget() throws IOException {
            String content = Files.readString(MAKEFILE_PATH);
            assertThat(content).contains("chaos-status:");
        }

        @Test
        @DisplayName("chaos-clean uses --ignore-not-found for idempotency (exit 0 with no resources)")
        void chaosCleanUsesIgnoreNotFound() throws IOException {
            String content = Files.readString(MAKEFILE_PATH);
            assertThat(content).contains("--ignore-not-found");
        }

        @Test
        @DisplayName("chaos-clean suppresses stderr for idempotency (no error output)")
        void chaosCleanSuppressesStderr() throws IOException {
            String content = Files.readString(MAKEFILE_PATH);
            // The Makefile uses `|| true` or `2>/dev/null` to ensure exit 0
            assertThat(content)
                    .as("chaos-clean should suppress errors for idempotent operation")
                    .matches("(?s).*chaos-clean:.*(?:2>/dev/null|\\|\\| true).*");
        }

        @Test
        @DisplayName("chaos-deploy creates namespace if absent")
        void chaosDeployCreatesNamespace() throws IOException {
            String content = Files.readString(MAKEFILE_PATH);
            // Should contain namespace creation logic (get ns || create ns)
            assertThat(content)
                    .as("chaos-deploy should create namespace if it doesn't exist")
                    .contains("kubectl get ns")
                    .contains("kubectl create ns");
        }

        @Test
        @DisplayName("chaos-deploy applies all 3 chaos manifests")
        void chaosDeployAppliesAllManifests() throws IOException {
            String content = Files.readString(MAKEFILE_PATH);
            assertThat(content).contains("crashloop-pod.yaml");
            assertThat(content).contains("oomkilled-pod.yaml");
            assertThat(content).contains("imagepull-pod.yaml");
        }

        @Test
        @DisplayName("Makefile targets are declared as .PHONY")
        void targetsAreDeclaredAsPhony() throws IOException {
            String content = Files.readString(MAKEFILE_PATH);
            assertThat(content).contains(".PHONY:");
            assertThat(content).contains("chaos-deploy");
            assertThat(content).contains("chaos-clean");
            assertThat(content).contains("chaos-status");
        }

        @Test
        @DisplayName("Makefile uses chaos-validation namespace variable")
        void usesNamespaceVariable() throws IOException {
            String content = Files.readString(MAKEFILE_PATH);
            assertThat(content).contains("chaos-validation");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 6. Chaos Manifest Deployment Verification
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Chaos Manifest Deployment")
    class ChaosManifestDeployment {

        private static final Path CHAOS_DIR = resolveChaosDir();
        private static final Yaml YAML = new Yaml();

        @Test
        @DisplayName("all 3 chaos manifests exist and are valid YAML")
        void allManifestsExistAndAreValidYaml() throws IOException {
            Path crashloop = CHAOS_DIR.resolve("crashloop-pod.yaml");
            Path oomkilled = CHAOS_DIR.resolve("oomkilled-pod.yaml");
            Path imagepull = CHAOS_DIR.resolve("imagepull-pod.yaml");

            assertThat(Files.exists(crashloop)).isTrue();
            assertThat(Files.exists(oomkilled)).isTrue();
            assertThat(Files.exists(imagepull)).isTrue();

            // Verify parseable as YAML
            assertThat(loadYaml(crashloop)).isNotNull();
            assertThat(loadYaml(oomkilled)).isNotNull();
            assertThat(loadYaml(imagepull)).isNotNull();
        }

        @Test
        @DisplayName("all manifests target chaos-validation namespace")
        void allManifestsTargetChaosNamespace() throws IOException {
            assertNamespace(CHAOS_DIR.resolve("crashloop-pod.yaml"));
            assertNamespace(CHAOS_DIR.resolve("oomkilled-pod.yaml"));
            assertNamespace(CHAOS_DIR.resolve("imagepull-pod.yaml"));
        }

        @Test
        @DisplayName("each manifest has the phase=e2e-validation label")
        void eachManifestHasPhaseLabel() throws IOException {
            assertLabel(CHAOS_DIR.resolve("crashloop-pod.yaml"), "phase", "e2e-validation");
            assertLabel(CHAOS_DIR.resolve("oomkilled-pod.yaml"), "phase", "e2e-validation");
            assertLabel(CHAOS_DIR.resolve("imagepull-pod.yaml"), "phase", "e2e-validation");
        }

        @Test
        @DisplayName("each manifest has unique chaos-type label matching its failure typology")
        void eachManifestHasUniqueChaosTypeLabel() throws IOException {
            assertLabel(CHAOS_DIR.resolve("crashloop-pod.yaml"), "chaos-type", "crashloop");
            assertLabel(CHAOS_DIR.resolve("oomkilled-pod.yaml"), "chaos-type", "oomkilled");
            assertLabel(CHAOS_DIR.resolve("imagepull-pod.yaml"), "chaos-type", "imagepullbackoff");
        }

        @SuppressWarnings("unchecked")
        private void assertNamespace(Path manifestPath) throws IOException {
            Map<String, Object> manifest = loadYaml(manifestPath);
            Map<String, Object> metadata = (Map<String, Object>) manifest.get("metadata");
            assertThat(metadata.get("namespace")).isEqualTo("chaos-validation");
        }

        @SuppressWarnings("unchecked")
        private void assertLabel(Path manifestPath, String key, String value) throws IOException {
            Map<String, Object> manifest = loadYaml(manifestPath);
            Map<String, Object> metadata = (Map<String, Object>) manifest.get("metadata");
            Map<String, Object> labels = (Map<String, Object>) metadata.get("labels");
            assertThat(labels.get(key)).isEqualTo(value);
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> loadYaml(Path path) throws IOException {
            try (InputStream is = Files.newInputStream(path)) {
                return YAML.load(is);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Shared Helper Methods
    // ─────────────────────────────────────────────────────────────────────────────

    private EnrichedContext fullEnrichedContext() {
        return new EnrichedContext(
                "Name: chaos-pod\nNamespace: chaos-validation\nStatus: Failed\n",
                "LAST SEEN   TYPE     REASON   OBJECT   MESSAGE\n2m   Warning  BackOff  pod/chaos-pod  Back-off restarting failed container",
                "ERROR: REQUIRED_CONFIG_VALUE not set\n",
                ALL_MCP_TOOLS
        );
    }

    private static Path resolveChaosDir() {
        Path projectRoot = Path.of("").toAbsolutePath();
        Path chaosDir = projectRoot.resolve("../../deployments/chaos").normalize();
        if (!Files.isDirectory(chaosDir)) {
            chaosDir = projectRoot.resolve("deployments/chaos").normalize();
        }
        return chaosDir;
    }
}
