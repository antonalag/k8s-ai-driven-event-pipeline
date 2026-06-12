import { JSON_RPC_ERRORS } from '../types.js';

/**
 * Custom JSON-RPC error codes for MCP Server tool errors.
 */
export const MCP_ERRORS = {
  POD_NOT_FOUND: -32001,
  LOGS_UNAVAILABLE: -32002,
  TIMEOUT: -32003,
  UPSTREAM_FAILURE: -32004,
} as const;

/**
 * Typed error thrown by tool handlers.
 * The rpc-handler catches these and converts them to JSON-RPC error responses.
 */
export class McpToolError extends Error {
  public readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'McpToolError';
    this.code = code;
  }
}

/**
 * Validates podName: must be 1–253 characters.
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
 * Validates namespace: must be 1–63 characters.
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
 * Returns synthetic mock data for describe_pod.
 */
function getMockPodDescription(_podName: string, _namespace: string): object {
  return {
    containers: [
      {
        name: 'main',
        image: 'nginx:1.27-alpine',
        resources: {
          limits: { cpu: '500m', memory: '256Mi' },
          requests: { cpu: '100m', memory: '128Mi' },
        },
        restartCount: 3,
        state: { waiting: { reason: 'CrashLoopBackOff' } },
      },
    ],
    phase: 'Failed',
    conditions: [
      { type: 'Ready', status: 'False', reason: 'ContainersNotReady' },
    ],
    nodeName: 'node-1',
  };
}

/**
 * Queries the Kubernetes API for pod details.
 * Throws McpToolError on failure or not found.
 */
async function getK8sPodDescription(podName: string, namespace: string): Promise<object> {
  try {
    const { KubeConfig, CoreV1Api } = await import('@kubernetes/client-node');
    const kc = new KubeConfig();
    kc.loadFromDefault();

    const k8sApi = kc.makeApiClient(CoreV1Api);
    const response = await k8sApi.readNamespacedPod({ name: podName, namespace });

    const pod = response;
    const containers = (pod.spec?.containers || []).map((container, idx) => {
      const status = pod.status?.containerStatuses?.[idx];
      const state: Record<string, unknown> = {};

      if (status?.state?.waiting) {
        state.waiting = { reason: status.state.waiting.reason || 'Unknown' };
      } else if (status?.state?.running) {
        state.running = { startedAt: status.state.running.startedAt };
      } else if (status?.state?.terminated) {
        state.terminated = {
          exitCode: status.state.terminated.exitCode,
          reason: status.state.terminated.reason || 'Unknown',
        };
      }

      return {
        name: container.name,
        image: container.image,
        resources: {
          limits: container.resources?.limits || {},
          requests: container.resources?.requests || {},
        },
        restartCount: status?.restartCount ?? 0,
        state,
      };
    });

    return {
      containers,
      phase: pod.status?.phase || 'Unknown',
      conditions: (pod.status?.conditions || []).map((c) => ({
        type: c.type,
        status: c.status,
        reason: c.reason || '',
      })),
      nodeName: pod.spec?.nodeName || '',
    };
  } catch (err: unknown) {
    if (err instanceof McpToolError) throw err;

    const error = err as { statusCode?: number; body?: { message?: string }; code?: string; message?: string };

    if (error.statusCode === 404 || error.body?.message?.includes('not found')) {
      throw new McpToolError(
        MCP_ERRORS.POD_NOT_FOUND,
        `Pod '${podName}' not found in namespace '${namespace}'`
      );
    }

    if (error.code === 'ECONNREFUSED' || error.code === 'ENOTFOUND' || error.code === 'ETIMEDOUT') {
      throw new McpToolError(
        MCP_ERRORS.UPSTREAM_FAILURE,
        `Kubernetes API unreachable: cluster communication failed for pod '${podName}' in namespace '${namespace}'`
      );
    }

    throw new McpToolError(
      MCP_ERRORS.UPSTREAM_FAILURE,
      `Kubernetes API error: ${error.message || 'unknown error'} for pod '${podName}' in namespace '${namespace}'`
    );
  }
}

/**
 * Handler for the describe_pod MCP tool.
 *
 * @param arguments - Record containing podName and namespace
 * @returns JSON-stringified pod description
 * @throws McpToolError with appropriate error code on failure
 */
export async function handleDescribePod(args: Record<string, unknown>): Promise<string> {
  const podName = validatePodName(args.podName);
  const namespace = validateNamespace(args.namespace);

  const mode = process.env.MCP_MODE || 'live';

  let result: object;
  if (mode === 'mock') {
    result = getMockPodDescription(podName, namespace);
  } else {
    result = await getK8sPodDescription(podName, namespace);
  }

  return JSON.stringify(result);
}
