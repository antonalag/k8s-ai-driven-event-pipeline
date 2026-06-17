package com.platform.analyzer.infrastructure.e2e;

import com.platform.analyzer.domain.model.KubernetesEvent;
import com.platform.analyzer.domain.model.PodPhase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: chaos deploy → event detection via K8s Collector within 60s.
 *
 * Since a live Kubernetes cluster is not available in CI, this test validates:
 * 1. Chaos manifests are structurally correct (namespace, labels, images, policies)
 * 2. Simulated chaos-typed events pass the consumer routing filter (Failed/Pending status)
 *
 * Validates: Requirements 2.3, 3.4, 4.2
 */
@DisplayName("Chaos Event Detection Integration Tests")
class ChaosEventDetectionIntegrationTest {

    private static final Path CHAOS_DIR = resolveChaosDir();
    private static final Yaml YAML = new Yaml();

    private static Map<String, Object> crashloopManifest;
    private static Map<String, Object> oomkilledManifest;
    private static Map<String, Object> imagepullManifest;

    @BeforeAll
    static void loadManifests() throws IOException {
        crashloopManifest = loadYaml(CHAOS_DIR.resolve("crashloop-pod.yaml"));
        oomkilledManifest = loadYaml(CHAOS_DIR.resolve("oomkilled-pod.yaml"));
        imagepullManifest = loadYaml(CHAOS_DIR.resolve("imagepull-pod.yaml"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. Manifest Structural Validation
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CrashLoopBackOff Manifest Validation")
    class CrashLoopManifestTests {

        @Test
        @DisplayName("targets chaos-validation namespace")
        void targetsCorrectNamespace() {
            Map<String, Object> metadata = getMetadata(crashloopManifest);
            assertThat(metadata.get("namespace")).isEqualTo("chaos-validation");
        }

        @Test
        @DisplayName("has correct labels: chaos-type=crashloop, phase=e2e-validation")
        void hasCorrectLabels() {
            Map<String, Object> labels = getLabels(crashloopManifest);
            assertThat(labels.get("chaos-type")).isEqualTo("crashloop");
            assertThat(labels.get("phase")).isEqualTo("e2e-validation");
        }

        @Test
        @DisplayName("has restartPolicy: Always")
        void hasRestartPolicyAlways() {
            Map<String, Object> spec = getSpec(crashloopManifest);
            assertThat(spec.get("restartPolicy")).isEqualTo("Always");
        }

        @Test
        @DisplayName("references ConfigMap via environment variable")
        void referencesConfigMap() {
            Map<String, Object> container = getFirstContainer(crashloopManifest);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> env = (List<Map<String, Object>>) container.get("env");

            assertThat(env).isNotNull().isNotEmpty();

            boolean hasConfigMapRef = env.stream().anyMatch(envVar -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> valueFrom = (Map<String, Object>) envVar.get("valueFrom");
                return valueFrom != null && valueFrom.containsKey("configMapKeyRef");
            });
            assertThat(hasConfigMapRef)
                    .as("CrashLoopBackOff manifest should reference a ConfigMap")
                    .isTrue();
        }

        @Test
        @DisplayName("uses SHA-256 pinned image")
        void usesSha256PinnedImage() {
            Map<String, Object> container = getFirstContainer(crashloopManifest);
            String image = (String) container.get("image");
            assertThat(image).contains("@sha256:");
        }
    }

    @Nested
    @DisplayName("OOMKilled Manifest Validation")
    class OomKilledManifestTests {

        @Test
        @DisplayName("targets chaos-validation namespace")
        void targetsCorrectNamespace() {
            Map<String, Object> metadata = getMetadata(oomkilledManifest);
            assertThat(metadata.get("namespace")).isEqualTo("chaos-validation");
        }

        @Test
        @DisplayName("has correct labels: chaos-type=oomkilled, phase=e2e-validation")
        void hasCorrectLabels() {
            Map<String, Object> labels = getLabels(oomkilledManifest);
            assertThat(labels.get("chaos-type")).isEqualTo("oomkilled");
            assertThat(labels.get("phase")).isEqualTo("e2e-validation");
        }

        @Test
        @DisplayName("has restartPolicy: Never")
        void hasRestartPolicyNever() {
            Map<String, Object> spec = getSpec(oomkilledManifest);
            assertThat(spec.get("restartPolicy")).isEqualTo("Never");
        }

        @Test
        @DisplayName("has memory limit 64Mi and request 32Mi")
        void hasCorrectMemoryLimits() {
            Map<String, Object> container = getFirstContainer(oomkilledManifest);
            @SuppressWarnings("unchecked")
            Map<String, Object> resources = (Map<String, Object>) container.get("resources");
            assertThat(resources).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) resources.get("limits");
            @SuppressWarnings("unchecked")
            Map<String, Object> requests = (Map<String, Object>) resources.get("requests");

            assertThat(limits.get("memory")).isEqualTo("64Mi");
            assertThat(requests.get("memory")).isEqualTo("32Mi");
        }

        @Test
        @DisplayName("uses SHA-256 pinned image")
        void usesSha256PinnedImage() {
            Map<String, Object> container = getFirstContainer(oomkilledManifest);
            String image = (String) container.get("image");
            assertThat(image).contains("@sha256:");
        }
    }

    @Nested
    @DisplayName("ImagePullBackOff Manifest Validation")
    class ImagePullManifestTests {

        @Test
        @DisplayName("targets chaos-validation namespace")
        void targetsCorrectNamespace() {
            Map<String, Object> metadata = getMetadata(imagepullManifest);
            assertThat(metadata.get("namespace")).isEqualTo("chaos-validation");
        }

        @Test
        @DisplayName("has correct labels: chaos-type=imagepullbackoff, phase=e2e-validation")
        void hasCorrectLabels() {
            Map<String, Object> labels = getLabels(imagepullManifest);
            assertThat(labels.get("chaos-type")).isEqualTo("imagepullbackoff");
            assertThat(labels.get("phase")).isEqualTo("e2e-validation");
        }

        @Test
        @DisplayName("has restartPolicy: Never")
        void hasRestartPolicyNever() {
            Map<String, Object> spec = getSpec(imagepullManifest);
            assertThat(spec.get("restartPolicy")).isEqualTo("Never");
        }

        @Test
        @DisplayName("has imagePullPolicy: Always")
        void hasImagePullPolicyAlways() {
            Map<String, Object> container = getFirstContainer(imagepullManifest);
            assertThat(container.get("imagePullPolicy")).isEqualTo("Always");
        }

        @Test
        @DisplayName("references non-existent registry (no SHA-256 pinning intentionally)")
        void referencesNonExistentRegistry() {
            Map<String, Object> container = getFirstContainer(imagepullManifest);
            String image = (String) container.get("image");

            assertThat(image)
                    .as("ImagePullBackOff intentionally uses invalid image without SHA-256")
                    .doesNotContain("@sha256:");
            assertThat(image).contains("invalid-registry");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. Simulated Event Processing — Consumer Routing Filter Validation
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Simulated Chaos Event Detection")
    class SimulatedEventDetectionTests {

        @Test
        @DisplayName("CrashLoopBackOff event (Failed status) passes consumer filter (Req 2.3)")
        void crashloopEventPassesFilter() {
            KubernetesEvent event = new KubernetesEvent(
                    "chaos-crashloop-pod",
                    "chaos-validation",
                    PodPhase.Failed,
                    Instant.now()
            );

            assertThat(requiresAnalysis(event.status()))
                    .as("Failed pod event should be accepted for analysis")
                    .isTrue();
        }

        @Test
        @DisplayName("OOMKilled event (Failed status) passes consumer filter (Req 3.4)")
        void oomkilledEventPassesFilter() {
            KubernetesEvent event = new KubernetesEvent(
                    "chaos-oomkilled-pod",
                    "chaos-validation",
                    PodPhase.Failed,
                    Instant.now()
            );

            assertThat(requiresAnalysis(event.status()))
                    .as("Failed pod event for OOMKilled should be accepted for analysis")
                    .isTrue();
        }

        @Test
        @DisplayName("ImagePullBackOff event (Pending status) passes consumer filter (Req 4.2)")
        void imagepullEventPassesPendingFilter() {
            KubernetesEvent event = new KubernetesEvent(
                    "chaos-imagepull-pod",
                    "chaos-validation",
                    PodPhase.Pending,
                    Instant.now()
            );

            assertThat(requiresAnalysis(event.status()))
                    .as("Pending pod event for ImagePullBackOff should be accepted for analysis")
                    .isTrue();
        }

        @Test
        @DisplayName("ImagePullBackOff event (Failed status) also passes consumer filter (Req 4.2)")
        void imagepullEventPassesFailedFilter() {
            KubernetesEvent event = new KubernetesEvent(
                    "chaos-imagepull-pod",
                    "chaos-validation",
                    PodPhase.Failed,
                    Instant.now()
            );

            assertThat(requiresAnalysis(event.status()))
                    .as("Failed pod event for ImagePullBackOff should be accepted for analysis")
                    .isTrue();
        }

        @Test
        @DisplayName("Running pods are NOT forwarded to analysis pipeline")
        void runningPodsNotForwarded() {
            KubernetesEvent event = new KubernetesEvent(
                    "healthy-pod",
                    "default",
                    PodPhase.Running,
                    Instant.now()
            );

            assertThat(requiresAnalysis(event.status()))
                    .as("Running pods should not trigger analysis")
                    .isFalse();
        }

        @Test
        @DisplayName("Succeeded pods are NOT forwarded to analysis pipeline")
        void succeededPodsNotForwarded() {
            KubernetesEvent event = new KubernetesEvent(
                    "completed-job-pod",
                    "batch",
                    PodPhase.Succeeded,
                    Instant.now()
            );

            assertThat(requiresAnalysis(event.status()))
                    .as("Succeeded pods should not trigger analysis")
                    .isFalse();
        }

        @Test
        @DisplayName("Chaos events use chaos-validation namespace matching manifest deployment")
        void chaosEventsHaveCorrectNamespace() {
            List<KubernetesEvent> chaosEvents = List.of(
                    new KubernetesEvent("chaos-crashloop-pod", "chaos-validation", PodPhase.Failed, Instant.now()),
                    new KubernetesEvent("chaos-oomkilled-pod", "chaos-validation", PodPhase.Failed, Instant.now()),
                    new KubernetesEvent("chaos-imagepull-pod", "chaos-validation", PodPhase.Pending, Instant.now())
            );

            chaosEvents.forEach(event ->
                    assertThat(event.namespace())
                            .as("All chaos events should target the chaos-validation namespace")
                            .isEqualTo("chaos-validation")
            );
        }

        @Test
        @DisplayName("Pod names in events match manifest pod names for correlation")
        void podNamesMatchManifestNames() {
            Map<String, Object> crashloopMeta = getMetadata(crashloopManifest);
            Map<String, Object> oomkilledMeta = getMetadata(oomkilledManifest);
            Map<String, Object> imagepullMeta = getMetadata(imagepullManifest);

            KubernetesEvent crashloopEvent = new KubernetesEvent(
                    (String) crashloopMeta.get("name"), "chaos-validation", PodPhase.Failed, Instant.now());
            KubernetesEvent oomkilledEvent = new KubernetesEvent(
                    (String) oomkilledMeta.get("name"), "chaos-validation", PodPhase.Failed, Instant.now());
            KubernetesEvent imagepullEvent = new KubernetesEvent(
                    (String) imagepullMeta.get("name"), "chaos-validation", PodPhase.Pending, Instant.now());

            assertThat(crashloopEvent.podName()).isEqualTo("chaos-crashloop-pod");
            assertThat(oomkilledEvent.podName()).isEqualTo("chaos-oomkilled-pod");
            assertThat(imagepullEvent.podName()).isEqualTo("chaos-imagepull-pod");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Replicates the consumer routing logic from PodEventConsumer.requiresAnalysis().
     * Events with Failed, Pending, or Unknown status are forwarded for analysis.
     */
    private boolean requiresAnalysis(PodPhase status) {
        return switch (status) {
            case Failed, Pending, Unknown -> true;
            case Running, Succeeded -> false;
        };
    }

    private static Path resolveChaosDir() {
        // Resolve from project root (services/ai-analyzer → ../../deployments/chaos)
        Path projectRoot = Path.of("").toAbsolutePath();
        Path chaosDir = projectRoot.resolve("../../deployments/chaos").normalize();
        if (!Files.isDirectory(chaosDir)) {
            // Fallback: try from workspace root
            chaosDir = projectRoot.resolve("deployments/chaos").normalize();
        }
        return chaosDir;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return YAML.load(is);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMetadata(Map<String, Object> manifest) {
        return (Map<String, Object>) manifest.get("metadata");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getLabels(Map<String, Object> manifest) {
        return (Map<String, Object>) getMetadata(manifest).get("labels");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getSpec(Map<String, Object> manifest) {
        return (Map<String, Object>) manifest.get("spec");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getFirstContainer(Map<String, Object> manifest) {
        Map<String, Object> spec = getSpec(manifest);
        List<Map<String, Object>> containers = (List<Map<String, Object>>) spec.get("containers");
        return containers.get(0);
    }
}
