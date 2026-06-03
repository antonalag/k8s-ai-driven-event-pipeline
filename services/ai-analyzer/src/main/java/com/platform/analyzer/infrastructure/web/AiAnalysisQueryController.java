package com.platform.analyzer.infrastructure.web;

import com.platform.analyzer.domain.model.AiAnalysisView;
import com.platform.analyzer.domain.ports.AiAnalysisQueryPort;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing the AI analysis query endpoint.
 * Delegates all business logic to {@link AiAnalysisQueryPort}.
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AiAnalysisQueryController {

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

        return results.stream()
                .map(AiAnalysisResponse::from)
                .toList();
    }
}
