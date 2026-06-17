package com.platform.analyzer.infrastructure.persistence.opensearch;

import com.platform.analyzer.domain.model.AiAnalysis;
import net.jqwik.api.*;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test verifying document ID format invariant.
 *
 * Property 5: Document ID format invariant
 * For any valid pod name and timestamp, the document ID equals {podName}-{analyzedAtEpochMillis}.
 *
 * Validates: Requirements 6.5
 */
@Tag("Feature: e2e-validation-diagnostic-calibration, Property 5: Document ID format invariant")
class AiAnalysisDocumentIdPropertyTest {

    /**
     * Pattern for valid document IDs: {podName}-{epochMillis}
     * podName = alphanumeric + hyphens; epochMillis = positive long number
     */
    private static final Pattern DOC_ID_PATTERN = Pattern.compile("^.+-\\d+$");

    // ─── Property 5a: Document ID matches format {podName}-{analyzedAtEpochMillis} ───

    @Property(tries = 100)
    void documentIdMatchesExpectedFormat(
            @ForAll("randomPodName") String podName,
            @ForAll("randomNamespace") String namespace,
            @ForAll("randomVerdict") String verdict,
            @ForAll("randomRootCause") String rootCause,
            @ForAll("randomActions") List<String> actions,
            @ForAll("randomMcpTools") List<String> mcpToolsUsed,
            @ForAll("randomBoolean") boolean mcpContextAvailable
    ) {
        AiAnalysis analysis = new AiAnalysis(
                podName, namespace, verdict, rootCause, actions,
                mcpToolsUsed, mcpContextAvailable
        );

        AiAnalysisDocument doc = AiAnalysisDocument.from(analysis);

        // ID must match the pattern {podName}-{epochMillis}
        assertThat(doc.getId())
                .as("Document ID must match pattern: {podName}-{epochMillis}")
                .matches(DOC_ID_PATTERN);
    }

    // ─── Property 5b: Document ID starts with the exact pod name ─────────────────

    @Property(tries = 100)
    void documentIdStartsWithPodName(
            @ForAll("randomPodName") String podName,
            @ForAll("randomNamespace") String namespace,
            @ForAll("randomVerdict") String verdict,
            @ForAll("randomRootCause") String rootCause,
            @ForAll("randomActions") List<String> actions
    ) {
        AiAnalysis analysis = new AiAnalysis(podName, namespace, verdict, rootCause, actions);

        AiAnalysisDocument doc = AiAnalysisDocument.from(analysis);

        assertThat(doc.getId())
                .as("Document ID must start with the pod name")
                .startsWith(podName + "-");
    }

    // ─── Property 5c: Epoch millis portion is a valid positive number ────────────

    @Property(tries = 100)
    void documentIdContainsValidEpochMillis(
            @ForAll("randomPodName") String podName,
            @ForAll("randomNamespace") String namespace,
            @ForAll("randomVerdict") String verdict,
            @ForAll("randomRootCause") String rootCause,
            @ForAll("randomActions") List<String> actions
    ) {
        AiAnalysis analysis = new AiAnalysis(podName, namespace, verdict, rootCause, actions);

        AiAnalysisDocument doc = AiAnalysisDocument.from(analysis);

        // Extract the epoch millis portion after the last hyphen
        String id = doc.getId();
        String prefix = podName + "-";
        assertThat(id).startsWith(prefix);

        String epochPart = id.substring(prefix.length());
        long epochMillis = Long.parseLong(epochPart);

        assertThat(epochMillis)
                .as("Epoch millis must be a positive number representing a valid timestamp")
                .isPositive();
    }

    // ─── Property 5d: Document ID is deterministic for same input at same instant ─

    @Property(tries = 50)
    void documentIdPodNameIsPreserved(
            @ForAll("randomPodName") String podName,
            @ForAll("randomNamespace") String namespace,
            @ForAll("randomVerdict") String verdict,
            @ForAll("randomRootCause") String rootCause,
            @ForAll("randomActions") List<String> actions
    ) {
        AiAnalysis analysis = new AiAnalysis(podName, namespace, verdict, rootCause, actions);

        AiAnalysisDocument doc = AiAnalysisDocument.from(analysis);

        // The pod name stored in the document must match the analysis pod name
        assertThat(doc.getPodName()).isEqualTo(podName);
        // analyzedAt must be set (non-null)
        assertThat(doc.getAnalyzedAt()).isNotNull();
        // ID must equal podName + "-" + analyzedAt epoch millis
        String expectedId = podName + "-" + doc.getAnalyzedAt().toEpochMilli();
        assertThat(doc.getId()).isEqualTo(expectedId);
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> randomPodName() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-')
                .ofMinLength(1)
                .ofMaxLength(63);
    }

    @Provide
    Arbitrary<String> randomNamespace() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-')
                .ofMinLength(1)
                .ofMaxLength(63);
    }

    @Provide
    Arbitrary<String> randomVerdict() {
        return Arbitraries.of("HEALTHY", "TRANSIENT_ISSUE", "CRITICAL_FAILURE", "DEGRADED");
    }

    @Provide
    Arbitrary<String> randomRootCause() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '.', ',', '-')
                .ofMinLength(5)
                .ofMaxLength(200);
    }

    @Provide
    Arbitrary<List<String>> randomActions() {
        Arbitrary<String> action = Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '-', '/')
                .ofMinLength(5)
                .ofMaxLength(100);
        return action.list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<String>> randomMcpTools() {
        return Arbitraries.of("describe_pod", "get_events", "get_logs")
                .list().ofMinSize(0).ofMaxSize(3).uniqueElements();
    }

    @Provide
    Arbitrary<Boolean> randomBoolean() {
        return Arbitraries.of(true, false);
    }
}
