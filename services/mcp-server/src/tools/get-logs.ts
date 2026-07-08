import { JSON_RPC_ERRORS } from '../types.js';
import { McpToolError, MCP_ERRORS } from './describe-pod.js';

/** Maximum log response size in bytes (32KB) */
const MAX_LOG_BYTES = 32_768;

/** Default tail lines */
const DEFAULT_TAIL_LINES = 100;

/** Min/max tail lines range */
const MIN_TAIL_LINES = 1;
const MAX_TAIL_LINES = 10_000;

/** Timeout for K8s API calls in milliseconds */
const K8S_TIMEOUT_MS = 10_000;

/**
 * Mock log data returned when MCP_MODE=mock.
 */
const MOCK_LOGS = `2024-01-15T10:28:00Z INFO  Starting application v2.1.0
2024-01-15T10:29:30Z WARN  Memory usage at 85%
2024-01-15T10:30:00Z ERROR java.lang.OutOfMemoryError: Java heap space
    at com.example.service.DataProcessor.process(DataProcessor.java:42)
    at com.example.service.EventHandler.handle(EventHandler.java:28)
2024-01-15T10:30:01Z ERROR Application terminated unexpectedly`;

/**
 * Response shape for get_logs tool.
 */
interface GetLogsResult {
  logs: string;
  truncated: boolean;
  containerName: string;
}

/**
 * Validates podName: must be non-empty, 1–253 characters.
 */
function validatePodName(podName: unknown): string {
  if (typeof podName !== 'string' || podName.length === 0) {
    throw new McpToolError(
      JSON_RPC_ERRORS.INVALID_PARAMS,
      'Invalid params: podName must be a non-empty string (1–253 characters)'
    );
  }
  if (podName.length > 253) {
    throw new McpToolError(
      JSON_RPC_ERRORS.INVALID_PARAMS,
      'Invalid params: podName exceeds maximum length of 253 characters'
    );
  }
  return podName;
}

/**
 * Validates namespace: must be non-empty, 1–63 characters.
 */
function validateNamespace(namespace: unknown): string {
  if (typeof namespace !== 'string' || namespace.length === 0) {
    throw new McpToolError(
      JSON_RPC_ERRORS.INVALID_PARAMS,
      'Invalid params: namespace must be a non-empty string (1–63 characters)'
    );
  }
  if (namespace.length > 63) {
    throw new McpToolError(
      JSON_RPC_ERRORS.INVALID_PARAMS,
      'Invalid params: namespace exceeds maximum length of 63 characters'
    );
  }
  return namespace;
}

/**
 * Validates tailLines: default 100, range [1, 10000].
 * Returns the validated number or throws McpToolError.
 */
function validateTailLines(tailLines: unknown): number {
  if (tailLines === undefined || tailLines === null) {
    return DEFAULT_TAIL_LINES;
  }
  const value = typeof tailLines === 'number' ? tailLines : Number(tailLines);
  if (!Number.isFinite(value) || !Number.isInteger(value)) {
    throw new McpToolError(
      JSON_RPC_ERRORS.INVALID_PARAMS,
      `Invalid params: tailLines must be an integer in the range [${MIN_TAIL_LINES}, ${MAX_TAIL_LINES}]`
    );
  }
  if (value < MIN_TAIL_LINES || value > MAX_TAIL_LINES) {
    throw new McpToolError(
      JSON_RPC_ERRORS.INVALID_PARAMS,
      `Invalid params: tailLines must be in the range [${MIN_TAIL_LINES}, ${MAX_TAIL_LINES}], got ${value}`
    );
  }
  return value;
}

/**
 * Truncates log content to MAX_LOG_BYTES (32KB).
 * If truncation is needed, keeps the last 32KB of content.
 */
function truncateLogs(logs: string): { logs: string; truncated: boolean } {
  const bytes = Buffer.byteLength(logs, 'utf-8');
  if (bytes <= MAX_LOG_BYTES) {
    return { logs, truncated: false };
  }
  const buffer = Buffer.from(logs, 'utf-8');
  const truncatedBuffer = buffer.subarray(buffer.length - MAX_LOG_BYTES);
  return { logs: truncatedBuffer.toString('utf-8'), truncated: true };
}

/**
 * Container selection logic:
 * - First non-Running container (in pod spec order)
 * - Or first container if all are Running
 */
function selectContainer(
  containers: Array<{ name: string; state?: Record<string, unknown> }>
): string {
  if (containers.length === 0) {
    return '';
  }
  for (const container of containers) {
    const state = container.state;
    if (state && !('running' in state)) {
      return container.name;
    }
  }
  return containers[0].name;
}

/**
 * Returns synthetic mock log data for get_logs.
 */
function getMockLogs(tailLines: number): GetLogsResult {
  const lines = MOCK_LOGS.split('\n');
  const selectedLines = lines.slice(-tailLines);
  const logContent = selectedLines.join('\n');
  const { logs, truncated } = truncateLogs(logContent);

  return {
    logs,
    truncated,
    containerName: 'main',
  };
}

/**
 * Queries K8s API for pod logs from the appropriate container.
 * Throws McpToolError on failure.
 */
async function getK8sLogs(
  podName: string,
  namespace: string,
  tailLines: number
): Promise<GetLogsResult> {
  const { KubeConfig, CoreV1Api } = await import('@kubernetes/client-node');
  const kc = new KubeConfig();
  kc.loadFromDefault();
  const k8sApi = kc.makeApiClient(CoreV1Api);

  let pod: Record<string, unknown>;
  try {
    pod = await Promise.race([
      k8sApi.readNamespacedPod({ name: podName, namespace }),
      new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('TIMEOUT')), K8S_TIMEOUT_MS)
      ),
    ]) as Record<string, unknown>;
  } catch (err: unknown) {
    if (err instanceof McpToolError) throw err;
    const error = err as Error & { statusCode?: number; response?: { statusCode?: number }; code?: string };
    if (error.message === 'TIMEOUT') {
      throw new McpToolError(MCP_ERRORS.TIMEOUT, 'Timeout retrieving logs: K8s API did not respond within 10 seconds');
    }
    const statusCode = error.statusCode || error.response?.statusCode;
    if (statusCode === 404) {
      throw new McpToolError(MCP_ERRORS.POD_NOT_FOUND, `Pod '${podName}' not found in namespace '${namespace}'`);
    }
    throw new McpToolError(MCP_ERRORS.LOGS_UNAVAILABLE, `Logs unavailable for pod '${podName}': ${error.message || 'unknown error'}`);
  }

  const podTyped = pod as {
    spec?: { containers?: Array<{ name: string }> };
    status?: { containerStatuses?: Array<{ name: string; state?: Record<string, unknown> }> };
  };
  const specContainers = podTyped.spec?.containers || [];
  const containerStatuses = podTyped.status?.containerStatuses || [];

  // Build container info combining spec order with status
  const containersWithState = specContainers.map((specContainer) => {
    const status = containerStatuses.find((cs) => cs.name === specContainer.name);
    return { name: specContainer.name, state: status?.state };
  });

  const containerName = selectContainer(containersWithState) || specContainers[0]?.name || 'main';

  let rawLogs: string;
  try {
    const logResponse = await Promise.race([
      k8sApi.readNamespacedPodLog({
        name: podName,
        namespace,
        container: containerName,
        tailLines,
      }),
      new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('TIMEOUT')), K8S_TIMEOUT_MS)
      ),
    ]);
    rawLogs = typeof logResponse === 'string' ? logResponse : String(logResponse);
  } catch (err: unknown) {
    if (err instanceof McpToolError) throw err;
    const error = err as Error & { statusCode?: number; response?: { statusCode?: number } };
    if (error.message === 'TIMEOUT') {
      throw new McpToolError(MCP_ERRORS.TIMEOUT, 'Timeout retrieving logs: K8s API did not respond within 10 seconds');
    }
    const statusCode = error.statusCode || error.response?.statusCode;
    if (statusCode === 400) {
      throw new McpToolError(MCP_ERRORS.LOGS_UNAVAILABLE, `Logs unavailable for pod '${podName}': container not started`);
    }
    throw new McpToolError(MCP_ERRORS.LOGS_UNAVAILABLE, `Logs unavailable for pod '${podName}': ${error.message || 'unknown error'}`);
  }

  const { logs, truncated } = truncateLogs(rawLogs);

  return {
    logs,
    truncated,
    containerName,
  };
}

/**
 * Handler for the get_logs MCP tool.
 *
 * Validates input parameters, queries the K8s API (or returns mock data),
 * and returns a JSON-stringified GetLogsResult.
 *
 * @param args - Record containing podName, namespace, and optional tailLines
 * @returns JSON-stringified GetLogsResult
 * @throws McpToolError with appropriate error code on failure
 */
export async function handleGetLogs(args: Record<string, unknown>): Promise<string> {
  const podName = validatePodName(args.podName);
  const namespace = validateNamespace(args.namespace);
  const tailLines = validateTailLines(args.tailLines);

  const mode = process.env.MCP_MODE || 'live';

  let result: GetLogsResult;
  if (mode === 'mock') {
    result = getMockLogs(tailLines);
  } else {
    result = await getK8sLogs(podName, namespace, tailLines);
  }

  return JSON.stringify(result);
}
