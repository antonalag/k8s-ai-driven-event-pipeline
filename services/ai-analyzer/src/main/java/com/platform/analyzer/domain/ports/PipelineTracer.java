package com.platform.analyzer.domain.ports;

/**
 * Port for structured pipeline tracing and observability.
 * Implementations emit structured log entries for E2E pipeline validation.
 */
public interface PipelineTracer {

    /**
     * Log the start of an analysis cycle with circuit breaker state.
     *
     * @param correlationId unique identifier for the pipeline cycle
     * @param cbState circuit breaker state (CLOSED, OPEN, HALF_OPEN)
     * @param podName the pod being analyzed
     * @param namespace the pod's namespace
     */
    void logCycleStart(String correlationId, String cbState, String podName, String namespace);

    /**
     * Log the result of an individual MCP tool invocation.
     *
     * @param correlationId unique identifier for the pipeline cycle
     * @param toolName MCP tool name (describe_pod, get_events, get_logs)
     * @param responseTimeMs response time in milliseconds
     * @param success whether the tool invocation succeeded
     */
    void logToolResult(String correlationId, String toolName, long responseTimeMs, boolean success);

    /**
     * Log the completion of a full pipeline cycle.
     *
     * @param correlationId unique identifier for the pipeline cycle
     * @param podName the pod that was analyzed
     * @param namespace the pod's namespace
     * @param toolsUsed number of MCP tools that returned successfully (0-3)
     * @param totalTimeMs total enrichment time in milliseconds
     * @param verdict the final analysis verdict
     */
    void logCycleComplete(String correlationId, String podName, String namespace,
                          int toolsUsed, long totalTimeMs, String verdict);

    /**
     * Log a warning when the pipeline cycle exceeds the 30-second threshold.
     *
     * @param correlationId unique identifier for the pipeline cycle
     * @param elapsedMs actual elapsed time in milliseconds
     */
    void logThresholdExceeded(String correlationId, long elapsedMs);
}
