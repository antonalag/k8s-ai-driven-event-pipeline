package com.platform.analyzer.infrastructure.adapter.outbound.persistence;

import com.platform.analyzer.domain.model.valueobject.AiAnalysisView;
import com.platform.analyzer.domain.port.inbound.AiAnalysisQueryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Adapter implementing AiAnalysisQueryPort using OpenSearch.
 * Returns only the latest active analysis per deployment — excludes resolved states
 * (DISMISSED, HEALTHY, DEGRADED, TRANSIENT_ISSUE, REMEDIATED) and deduplicates by deployment.
 */
@Component
@ConditionalOnProperty(name = "platform.storage.type", havingValue = "opensearch")
public class OpenSearchAnalysisQueryAdapter implements AiAnalysisQueryPort {

    private static final Set<String> EXCLUDED_STATUSES = Set.of("DISMISSED", "REMEDIATED");
    private static final Set<String> EXCLUDED_VERDICTS = Set.of("HEALTHY", "DEGRADED", "TRANSIENT_ISSUE");

    private final SpringDataAiAnalysisRepository repository;

    public OpenSearchAnalysisQueryAdapter(SpringDataAiAnalysisRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AiAnalysisView> findAll() {
        List<AiAnalysisDocument> allDocs = StreamSupport.stream(repository.findAll().spliterator(), false)
                .filter(this::isActiveAnalysis)
                .toList();
        return latestPerDeployment(allDocs);
    }

    @Override
    public List<AiAnalysisView> findByNamespace(String namespace) {
        List<AiAnalysisDocument> docs = repository.findByNamespaceOrderByAnalyzedAtDesc(namespace)
                .stream()
                .filter(this::isActiveAnalysis)
                .toList();
        return latestPerDeployment(docs);
    }

    @Override
    public List<AiAnalysisView> findByPodName(String podName) {
        List<AiAnalysisDocument> docs = repository.findByPodNameOrderByAnalyzedAtDesc(podName)
                .stream()
                .filter(this::isActiveAnalysis)
                .toList();
        return latestPerDeployment(docs);
    }

    /**
     * Filters out documents that represent resolved or excluded states.
     * A document is "active" if it has a non-blank verdict, is not DISMISSED/REMEDIATED,
     * and its verdict is not HEALTHY/DEGRADED/TRANSIENT_ISSUE.
     * Documents with null or blank verdict are corrupt/intermediate and excluded.
     */
    private boolean isActiveAnalysis(AiAnalysisDocument doc) {
        if (doc.getVerdict() == null || doc.getVerdict().isBlank()) {
            return false;
        }
        if (doc.getStatus() != null && EXCLUDED_STATUSES.contains(doc.getStatus())) {
            return false;
        }
        if (EXCLUDED_VERDICTS.contains(doc.getVerdict())) {
            return false;
        }
        return true;
    }

    /**
     * Reduces the result set to only the most recent analysis per deployment.
     * Groups by deployment name (pod name prefix without ReplicaSet and pod hash suffixes).
     */
    private List<AiAnalysisView> latestPerDeployment(List<AiAnalysisDocument> docs) {
        Map<String, AiAnalysisDocument> latestByDeployment = docs.stream()
                .collect(Collectors.toMap(
                        this::extractDeploymentPrefix,
                        doc -> doc,
                        (existing, replacement) -> {
                            if (existing.getAnalyzedAt() == null) return replacement;
                            if (replacement.getAnalyzedAt() == null) return existing;
                            return existing.getAnalyzedAt().isAfter(replacement.getAnalyzedAt())
                                    ? existing : replacement;
                        }
                ));

        return latestByDeployment.values().stream()
                .sorted(Comparator.comparing(
                        AiAnalysisDocument::getAnalyzedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AiAnalysisDocument::toView)
                .toList();
    }

    /**
     * Extracts the deployment prefix from a pod name.
     * Pod names follow: {deployment}-{replicaSetHash}-{podHash}
     * e.g., "golden-path-app-5f548d69d9-kjwwg" → "golden-path-app"
     */
    private String extractDeploymentPrefix(AiAnalysisDocument doc) {
        String podName = doc.getPodName();
        if (podName == null) return "unknown";
        int lastDash = podName.lastIndexOf('-');
        if (lastDash > 0) {
            int secondLastDash = podName.lastIndexOf('-', lastDash - 1);
            if (secondLastDash > 0) {
                return podName.substring(0, secondLastDash);
            }
        }
        return podName;
    }
}
