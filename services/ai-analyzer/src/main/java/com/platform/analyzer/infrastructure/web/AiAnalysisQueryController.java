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

    private static final List<String> EXCLUDED_VERDICTS = List.of("HEALTHY", "DEGRADED");

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
     * Reduces the result set to only the most recent analysis per pod.
     * Excludes pods whose latest verdict indicates a resolved state (HEALTHY, DEGRADED).
     */
    private List<AiAnalysisView> latestPerPod(List<AiAnalysisView> analyses) {
        // Group by podName, keep only the most recent entry per pod
        Map<String, AiAnalysisView> latestByPod = analyses.stream()
                .collect(Collectors.toMap(
                        AiAnalysisView::podName,
                        view -> view,
                        (existing, replacement) ->
                                existing.analyzedAt().isAfter(replacement.analyzedAt()) ? existing : replacement
                ));

        // Filter out pods whose latest verdict is resolved
        return latestByPod.values().stream()
                .filter(view -> !EXCLUDED_VERDICTS.contains(view.verdict()))
                .sorted(Comparator.comparing(AiAnalysisView::analyzedAt).reversed())
                .toList();
    }
}
