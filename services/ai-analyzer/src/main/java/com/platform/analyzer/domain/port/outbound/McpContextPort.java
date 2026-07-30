package com.platform.analyzer.domain.port.outbound;

import com.platform.analyzer.domain.model.valueobject.EnrichedContext;

/**
 * Port for retrieving enriched Kubernetes cluster context via MCP (Model Context Protocol)
 * for a given pod. Implementations (MCP Client adapters) reside in infrastructure/.
 */
public interface McpContextPort {

    EnrichedContext retrieveContext(String podName, String namespace);
}
