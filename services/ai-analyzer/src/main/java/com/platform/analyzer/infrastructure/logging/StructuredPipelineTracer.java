package com.platform.analyzer.infrastructure.logging;

import com.platform.analyzer.domain.ports.PipelineTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Structured JSON pipeline tracer for E2E observability.
 * Emits machine-parseable log entries (one JSON object per line) with correlation IDs
 * as first-class fields, enabling trace filtering in OpenSearch/CloudWatch.
 */
@Component
public class StructuredPipelineTracer implements PipelineTracer {

    private static final Logger log = LoggerFactory.getLogger(StructuredPipelineTracer.class);

    private static final long THRESHOLD_MS = 30_000;

    @Override
    public void logCycleStart(String correlationId, String cbState, String podName, String namespace) {
        log.info("{\"timestamp\":\"{}\",\"level\":\"INFO\",\"correlationId\":\"{}\",\"event\":\"cycle_start\","
                        + "\"cbState\":\"{}\",\"podName\":\"{}\",\"namespace\":\"{}\"}",
                Instant.now(), correlationId, cbState, podName, namespace);
    }

    @Override
    public void logToolResult(String correlationId, String toolName, long responseTimeMs, boolean success) {
        log.info("{\"timestamp\":\"{}\",\"level\":\"INFO\",\"correlationId\":\"{}\",\"event\":\"mcp_tool_complete\","
                        + "\"toolName\":\"{}\",\"responseTimeMs\":{},\"success\":{}}",
                Instant.now(), correlationId, toolName, responseTimeMs, success);
    }

    @Override
    public void logCycleComplete(String correlationId, String podName, String namespace,
                                  int toolsUsed, long totalTimeMs, String verdict) {
        log.info("{\"timestamp\":\"{}\",\"level\":\"INFO\",\"correlationId\":\"{}\",\"event\":\"cycle_complete\","
                        + "\"podName\":\"{}\",\"namespace\":\"{}\",\"toolsUsed\":{},\"totalTimeMs\":{},\"verdict\":\"{}\"}",
                Instant.now(), correlationId, podName, namespace, toolsUsed, totalTimeMs, verdict);
    }

    @Override
    public void logThresholdExceeded(String correlationId, long elapsedMs) {
        log.warn("{\"timestamp\":\"{}\",\"level\":\"WARN\",\"correlationId\":\"{}\",\"event\":\"threshold_exceeded\","
                        + "\"elapsedMs\":{},\"thresholdMs\":{}}",
                Instant.now(), correlationId, elapsedMs, THRESHOLD_MS);
    }
}
