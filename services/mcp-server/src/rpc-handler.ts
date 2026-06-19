import {
  type JsonRpcRequest,
  type JsonRpcResponse,
  type JsonRpcErrorResponse,
  type JsonRpcSuccessResponse,
  JSON_RPC_ERRORS,
  TOOL_WHITELIST,
  isWriteTool,
} from './types.js';
import { handleGetLogs } from './tools/get-logs.js';
import { handleGetEvents } from './tools/get-events.js';
import { handleDescribePod, McpToolError } from './tools/describe-pod.js';
import { handleRestartDeployment } from './tools/restart-deployment.js';
import { handleScaleDeployment } from './tools/scale-deployment.js';
import { handleFixContainerImage } from './tools/fix-container-image.js';
import { authorizeWriteOperation } from './middleware/rbac-gate.js';
import { getCache, setCache, buildCacheKey } from './middleware/idempotency-cache.js';

/**
 * Creates a JSON-RPC 2.0 error response.
 */
export function createErrorResponse(
  id: string | number | null,
  code: number,
  message: string
): JsonRpcErrorResponse {
  return {
    jsonrpc: '2.0',
    id,
    error: { code, message },
  };
}

/**
 * Creates a JSON-RPC 2.0 success response with MCP content blocks.
 */
export function createSuccessResponse(
  id: string | number | null,
  text: string
): JsonRpcSuccessResponse {
  return {
    jsonrpc: '2.0',
    id,
    result: {
      content: [{ type: 'text', text }],
    },
  };
}

/**
 * Validates that a parsed object conforms to JSON-RPC 2.0 format.
 */
function isValidJsonRpcRequest(obj: unknown): obj is JsonRpcRequest {
  if (typeof obj !== 'object' || obj === null) return false;
  const req = obj as Record<string, unknown>;
  if (req.jsonrpc !== '2.0') return false;
  if (typeof req.method !== 'string') return false;
  if (!('id' in req)) return false;
  return true;
}

/**
 * Checks if the tool name is in the whitelist.
 */
function isWhitelistedTool(name: string): boolean {
  return (TOOL_WHITELIST as readonly string[]).includes(name);
}

/**
 * Dispatches a validated read-only tool call to the appropriate handler.
 */
async function dispatchReadTool(
  toolName: string,
  args: Record<string, unknown>
): Promise<string> {
  switch (toolName) {
    case 'describe_pod':
      return await handleDescribePod(args);
    case 'get_events':
      return await handleGetEvents(args);
    case 'get_logs':
      return await handleGetLogs(args);
    default:
      throw new McpToolError(
        JSON_RPC_ERRORS.METHOD_NOT_FOUND,
        `Tool '${toolName}' is not implemented`
      );
  }
}

/**
 * Dispatches a validated write-back tool call to the appropriate handler.
 * Write tools go through RBAC gate and idempotency cache.
 */
async function dispatchWriteTool(
  toolName: string,
  args: Record<string, unknown>
): Promise<string> {
  // Extract namespace for RBAC validation
  const namespace = typeof args.namespace === 'string' ? args.namespace : '';

  // RBAC gate: validates namespace authorization (throws McpToolError on deny)
  authorizeWriteOperation(toolName, namespace);

  // Idempotency: check cache if correlationId is provided
  const correlationId = typeof args.correlationId === 'string' ? args.correlationId : undefined;
  if (correlationId) {
    const cacheKey = buildCacheKey(correlationId, toolName);
    const cached = getCache(cacheKey);
    if (cached !== undefined) {
      return cached;
    }
  }

  // Dispatch to the appropriate write tool handler
  let result: string;
  switch (toolName) {
    case 'restart_deployment':
      result = await handleRestartDeployment(args);
      break;
    case 'scale_deployment':
      result = await handleScaleDeployment(args);
      break;
    case 'fix_container_image':
      result = await handleFixContainerImage(args);
      break;
    default:
      throw new McpToolError(
        JSON_RPC_ERRORS.METHOD_NOT_FOUND,
        `Write tool '${toolName}' is not implemented`
      );
  }

  // Store in idempotency cache on success
  if (correlationId) {
    const cacheKey = buildCacheKey(correlationId, toolName);
    setCache(cacheKey, result);
  }

  return result;
}

/**
 * Handles a raw JSON string as a JSON-RPC 2.0 request and returns the response.
 */
export async function handleJsonRpcRequest(rawBody: string): Promise<JsonRpcResponse> {
  // Step 1: Parse JSON
  let parsed: unknown;
  try {
    parsed = JSON.parse(rawBody);
  } catch {
    return createErrorResponse(null, JSON_RPC_ERRORS.PARSE_ERROR, 'Parse error: invalid JSON');
  }

  // Step 2: Validate JSON-RPC 2.0 structure
  if (!isValidJsonRpcRequest(parsed)) {
    return createErrorResponse(null, JSON_RPC_ERRORS.INVALID_REQUEST, 'Invalid Request: not a valid JSON-RPC 2.0 request');
  }

  const request = parsed;
  const id = request.id;

  // Step 3: Validate method is "tools/call"
  if (request.method !== 'tools/call') {
    return createErrorResponse(id, JSON_RPC_ERRORS.METHOD_NOT_FOUND, `Method '${request.method}' not found`);
  }

  // Step 4: Validate params and tool name
  if (!request.params || typeof request.params.name !== 'string') {
    return createErrorResponse(id, JSON_RPC_ERRORS.INVALID_REQUEST, 'Invalid Request: params.name is required');
  }

  const toolName = request.params.name;

  // Step 5: Check tool whitelist
  if (!isWhitelistedTool(toolName)) {
    const allowed = TOOL_WHITELIST.join(', ');
    return createErrorResponse(
      id,
      JSON_RPC_ERRORS.METHOD_NOT_FOUND,
      `Tool '${toolName}' is not whitelisted. Allowed tools: ${allowed}`
    );
  }

  // Step 6: Dispatch to appropriate handler (read vs write path)
  const args = request.params.arguments || {};
  try {
    let resultText: string;
    if (isWriteTool(toolName)) {
      resultText = await dispatchWriteTool(toolName, args);
    } else {
      resultText = await dispatchReadTool(toolName, args);
    }
    return createSuccessResponse(id, resultText);
  } catch (err: unknown) {
    if (err instanceof McpToolError) {
      return createErrorResponse(id, err.code, err.message);
    }
    return createErrorResponse(
      id,
      JSON_RPC_ERRORS.INVALID_PARAMS,
      `Internal error executing tool '${toolName}': ${(err as Error).message || 'unknown error'}`
    );
  }
}
