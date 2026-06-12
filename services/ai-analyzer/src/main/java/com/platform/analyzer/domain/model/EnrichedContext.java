package com.platform.analyzer.domain.model;

import java.util.List;

/**
 * Aggregated MCP context for a failing pod.
 * Immutable value object — all fields nullable (partial enrichment allowed).
 */
public record EnrichedContext(
    String podDescription,
    String podEvents,
    String podLogs,
    List<String> toolsUsed
) {
    public static final EnrichedContext EMPTY =
        new EnrichedContext(null, null, null, List.of());

    public boolean hasContent() {
        return podDescription != null || podEvents != null || podLogs != null;
    }
}
