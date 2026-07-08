import { MCP_ERRORS, WRITE_TOOL_WHITELIST } from '../types.js';
import { McpToolError } from '../tools/describe-pod.js';

/**
 * RBAC Simulation Gate for write-back tools.
 *
 * Validates namespace authorization before permitting mutation operations.
 * Controlled via MCP_ALLOWED_NAMESPACES environment variable (comma-separated).
 * Fails closed: if unset or empty, ALL write operations are denied.
 */

export interface RbacDecision {
  timestamp: string;
  tool: string;
  namespace: string;
  decision: 'ALLOW' | 'DENY';
  reason: string;
}

/**
 * Parses the MCP_ALLOWED_NAMESPACES environment variable into a Set.
 */
function getAllowedNamespaces(): Set<string> {
  const raw = process.env.MCP_ALLOWED_NAMESPACES || '';
  if (raw.trim() === '') {
    return new Set();
  }
  return new Set(
    raw.split(',')
      .map(ns => ns.trim())
      .filter(ns => ns.length > 0)
  );
}

/**
 * Logs an RBAC decision as structured JSON to stdout.
 */
function logDecision(decision: RbacDecision): void {
  console.log(JSON.stringify(decision));
}

/**
 * Validates that a write-back tool invocation is authorized for the given namespace.
 *
 * @param toolName - The write-back tool being invoked.
 * @param namespace - The target Kubernetes namespace.
 * @throws McpToolError with code FORBIDDEN (-32403) if denied.
 */
export function authorizeWriteOperation(toolName: string, namespace: string): void {
  const allowedNamespaces = getAllowedNamespaces();

  // Fail closed: no namespaces configured → deny all
  if (allowedNamespaces.size === 0) {
    const decision: RbacDecision = {
      timestamp: new Date().toISOString(),
      tool: toolName,
      namespace,
      decision: 'DENY',
      reason: 'No namespaces authorized for write operations',
    };
    logDecision(decision);
    throw new McpToolError(
      MCP_ERRORS.FORBIDDEN,
      'No namespaces authorized for write operations'
    );
  }

  if (!(WRITE_TOOL_WHITELIST as readonly string[]).includes(toolName)) {
    const decision: RbacDecision = {
      timestamp: new Date().toISOString(),
      tool: toolName,
      namespace,
      decision: 'DENY',
      reason: `Tool '${toolName}' is not authorized for write operations`,
    };
    logDecision(decision);
    throw new McpToolError(
      MCP_ERRORS.FORBIDDEN,
      `Tool '${toolName}' is not authorized for write operations`
    );
  }

  if (!allowedNamespaces.has(namespace)) {
    const decision: RbacDecision = {
      timestamp: new Date().toISOString(),
      tool: toolName,
      namespace,
      decision: 'DENY',
      reason: `Namespace '${namespace}' is not authorized for write operations`,
    };
    logDecision(decision);
    throw new McpToolError(
      MCP_ERRORS.FORBIDDEN,
      `Namespace '${namespace}' is not authorized for write operations`
    );
  }

  const decision: RbacDecision = {
    timestamp: new Date().toISOString(),
    tool: toolName,
    namespace,
    decision: 'ALLOW',
    reason: 'Namespace is in MCP_ALLOWED_NAMESPACES allowlist',
  };
  logDecision(decision);
}
