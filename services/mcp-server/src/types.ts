/**
 * JSON-RPC 2.0 TypeScript interfaces for MCP Server.
 */

export interface JsonRpcRequest {
  jsonrpc: string;
  method: string;
  params?: JsonRpcParams;
  id: string | number | null;
}

export interface JsonRpcParams {
  name: string;
  arguments?: Record<string, unknown>;
}

export interface JsonRpcSuccessResponse {
  jsonrpc: '2.0';
  id: string | number | null;
  result: JsonRpcResult;
}

export interface JsonRpcErrorResponse {
  jsonrpc: '2.0';
  id: string | number | null;
  error: JsonRpcError;
}

export type JsonRpcResponse = JsonRpcSuccessResponse | JsonRpcErrorResponse;

export interface JsonRpcResult {
  content: ContentBlock[];
}

export interface ContentBlock {
  type: string;
  text: string;
}

export interface JsonRpcError {
  code: number;
  message: string;
}

/** Standard JSON-RPC 2.0 error codes */
export const JSON_RPC_ERRORS = {
  PARSE_ERROR: -32700,
  INVALID_REQUEST: -32600,
  METHOD_NOT_FOUND: -32601,
  INVALID_PARAMS: -32602,
} as const;

/** MCP Server custom error codes */
export const MCP_ERRORS = {
  RESOURCE_NOT_FOUND: -32001,
  LOGS_UNAVAILABLE: -32002,
  TIMEOUT: -32003,
  UPSTREAM_FAILURE: -32004,
  FORBIDDEN: -32403,
} as const;

/** Read-only MCP tool names */
export const READ_TOOL_WHITELIST = ['describe_pod', 'get_events', 'get_logs'] as const;

/** Write-back MCP tool names */
export const WRITE_TOOL_WHITELIST = ['restart_deployment', 'scale_deployment', 'fix_container_image'] as const;

/** Combined whitelisted MCP tool names */
export const TOOL_WHITELIST = [...READ_TOOL_WHITELIST, ...WRITE_TOOL_WHITELIST] as const;

export type ReadTool = (typeof READ_TOOL_WHITELIST)[number];
export type WriteTool = (typeof WRITE_TOOL_WHITELIST)[number];
export type WhitelistedTool = (typeof TOOL_WHITELIST)[number];

/** Structured result for write-back tool operations */
export interface WriteToolResult {
  action: string;
  status: 'completed' | 'failed';
  deploymentName: string;
  namespace: string;
  timestamp: string;
  details?: Record<string, unknown>;
}

/**
 * Checks if a tool name is a write-back tool.
 */
export function isWriteTool(name: string): name is WriteTool {
  return (WRITE_TOOL_WHITELIST as readonly string[]).includes(name);
}
