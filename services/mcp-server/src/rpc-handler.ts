import {
  type JsonRpcRequest,
  type JsonRpcResponse,
  type JsonRpcErrorResponse,
  type JsonRpcSuccessResponse,
  JSON_RPC_ERRORS,
  TOOL_WHITELIST,
} from './types.js';
import { handleGetLogs } from './tools/get-logs.js';
import { handleGetEvents } from './tools/get-events.js';
import { handleDescribePod, McpToolError } from './tools/describe-pod.js';

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
 * Dispatches a validated tool call to the appropriate handler.
 * Returns the full JsonRpcResponse for tools that manage their own responses,
 * or throws McpToolError for describe_pod which uses the throw pattern.
 */
async function dispatchTool(
  id: string | number | null,
  toolName: string,
  args: Record<string, unknown>
): Promise<JsonRpcResponse> {
  switch (toolName) {
    case 'describe_pod': {
      const result = await handleDescribePod(args);
      return createSuccessResponse(id, result);
    }
    case 'get_events': {
      const eventsResult = await handleGetEvents(args);
      return createSuccessResponse(id, eventsResult);
    }
    case 'get_logs': {
      const logsResult = await handleGetLogs(args);
      return createSuccessResponse(id, logsResult);
    }
    default:
      throw new McpToolError(
        JSON_RPC_ERRORS.METHOD_NOT_FOUND,
        `Tool '${toolName}' is not implemented`
      );
  }
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

  // Step 6: Dispatch to tool handler
  const args = request.params.arguments || {};
  try {
    return await dispatchTool(id, toolName, args);
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
