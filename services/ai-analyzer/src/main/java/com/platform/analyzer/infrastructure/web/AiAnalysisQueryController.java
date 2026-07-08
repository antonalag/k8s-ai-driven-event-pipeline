package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.model.AiAnalysisView;
import com.platform.analyzer.domain.ports.AiAnalysisQueryPort;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller exposing the AI analysis query endpoint.
 * Returns only the latest analysis per pod, excluding pods whose
 * latest verdict is HEALTHY or DEGRADED (resolved or fallback states).
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AiAnalysisQueryController {

    private static final List<String> EXCLUDED_VERDICTS = List.of("HEALTHY", "DEGRADED", "TRANSIENT_ISSUE");

    private final AiAnalysisQueryPort queryPort;

    public AiAnalysisQueryController(AiAnalysisQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @GetMapping
    public List<AiAnalysisResponse> query(
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String podName) {

        List<AiAnalysisView> results;

        if (StringUtils.hasText(namespace)) {
            results = queryPort.findByNamespace(namespace);
        } else if (StringUtils.hasText(podName)) {
            results = queryPort.findByPodName(podName);
        } else {
            results = queryPort.findAll();
        }

        return latestPerPod(results).stream()
                .map(AiAnalysisResponse::from)
                .toList();
    }

    /**
     * Reduces the result set to only the most recent analysis per deployment.
     * Groups by deployment name (pod name prefix without ReplicaSet and pod hash suffixes).
     * Excludes deployments whose latest verdict indicates a resolved state (HEALTHY, DEGRADED).
     */
    private List<AiAnalysisView> latestPerPod(List<AiAnalysisView> analyses) {
        Map<String, AiAnalysisView> latestByDeployment = analyses.stream()
                .collect(Collectors.toMap(
                        this::extractDeploymentPrefix,
                        view -> view,
                        (existing, replacement) ->
                                existing.analyzedAt().isAfter(replacement.analyzedAt()) ? existing : replacement
                ));

        return latestByDeployment.values().stream()
                .filter(view -> !EXCLUDED_VERDICTS.contains(view.verdict()))
                .sorted(Comparator.comparing(AiAnalysisView::analyzedAt).reversed())
                .toList();
    }

    /**
     * Extracts the deployment name from a pod name.
     * Pod names follow: {deployment}-{replicaSetHash}-{podHash}
     * e.g., "golden-path-app-5f548d69d9-kjwwg" → "golden-path-app"
     */
    private String extractDeploymentPrefix(AiAnalysisView view) {
        String podName = view.podName();
        // Remove last two hyphen-separated segments (replicaSet hash + pod hash)
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
