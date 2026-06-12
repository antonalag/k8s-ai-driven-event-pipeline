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

/** Whitelisted MCP tool names */
export const TOOL_WHITELIST = ['describe_pod', 'get_events', 'get_logs'] as const;

export type WhitelistedTool = (typeof TOOL_WHITELIST)[number];
