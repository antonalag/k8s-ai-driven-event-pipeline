import { JSON_RPC_ERRORS } from '../types.js';
import { McpToolError, MCP_ERRORS } from './describe-pod.js';

/** Maximum characters for event message field before truncation */
const MAX_MESSAGE_LENGTH = 1024;

/** Default maximum number of events to return */
const DEFAULT_MAX_EVENTS = 20;

/** Minimum configurable max events */
const MIN_MAX_EVENTS = 1;

/** Maximum configurable max events */
const MAX_MAX_EVENTS = 100;

/** Timeout in milliseconds for K8s API calls */
const K8S_TIMEOUT_MS = 5000;

/**
 * Represents a single Kubernetes event in the MCP response format.
 */
export interface EventEntry {
  type: 'Normal' | 'Warning';
  reason: string;
  message: string;
  sourceComponent: string;
  firstTimestamp: string;
  lastTimestamp: string;
  count: number;
}

/**
 * Validates podName: must be non-empty, max 253 chars.
 * Throws McpToolError with INVALID_PARAMS on failure.
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
 * Validates namespace: must be non-empty, max 63 chars.
 * Throws McpToolError with INVALID_PARAMS on failure.
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
 * Resolves the maximum events count from environment or defaults.
 */
function resolveMaxEvents(): number {
  const envVal = process.env.MCP_MAX_EVENTS;
  if (envVal) {
    const parsed = parseInt(envVal, 10);
    if (!isNaN(parsed) && parsed >= MIN_MAX_EVENTS && parsed <= MAX_MAX_EVENTS) {
      return parsed;
    }
  }
  return DEFAULT_MAX_EVENTS;
}

/**
 * Truncates a message string to MAX_MESSAGE_LENGTH with trailing ellipsis "...".
 */
function truncateMessage(message: string): string {
  if (message.length <= MAX_MESSAGE_LENGTH) {
    return message;
  }
  return message.substring(0, MAX_MESSAGE_LENGTH - 3) + '...';
}

/**
 * Mock events returned when MCP_MODE=mock.
 */
function getMockEvents(): EventEntry[] {
  return [
    {
      type: 'Warning',
      reason: 'BackOff',
      message: 'Back-off restarting failed container',
      sourceComponent: 'kubelet',
      firstTimestamp: '2024-01-15T10:00:00Z',
      lastTimestamp: '2024-01-15T10:30:00Z',
      count: 5,
    },
    {
      type: 'Warning',
      reason: 'OOMKilling',
      message: 'Memory limit exceeded',
      sourceComponent: 'kubelet',
      firstTimestamp: '2024-01-15T09:55:00Z',
      lastTimestamp: '2024-01-15T09:55:00Z',
      count: 1,
    },
  ];
}

/**
 * Queries the Kubernetes API for events filtered by pod involvedObject.
 * Returns events sorted by lastTimestamp descending, limited to maxEvents.
 */
async function fetchEventsFromK8s(
  podName: string,
  namespace: string,
  maxEvents: number
): Promise<EventEntry[]> {
  const { KubeConfig, CoreV1Api } = await import('@kubernetes/client-node');

  const kc = new KubeConfig();
  kc.loadFromDefault();
  const coreApi = kc.makeApiClient(CoreV1Api);

  const fieldSelector = `involvedObject.name=${podName},involvedObject.namespace=${namespace}`;

  const response = await coreApi.listNamespacedEvent({
    namespace,
    fieldSelector,
  });

  const items = response.items || [];

  const sorted = items.sort((a, b) => {
    const tsA = a.lastTimestamp ? new Date(a.lastTimestamp as unknown as string).getTime() : 0;
    const tsB = b.lastTimestamp ? new Date(b.lastTimestamp as unknown as string).getTime() : 0;
    return tsB - tsA;
  });

  const limited = sorted.slice(0, maxEvents);

  return limited.map((event): EventEntry => {
    const type: 'Normal' | 'Warning' = event.type === 'Normal' ? 'Normal' : 'Warning';
    const reason = event.reason || 'Unknown';
    const rawMessage = event.message || '';
    const message = truncateMessage(rawMessage);
    const sourceComponent = event.source?.component || 'unknown';

    const firstTimestamp = event.firstTimestamp
      ? new Date(event.firstTimestamp as unknown as string).toISOString()
      : new Date().toISOString();
    const lastTimestamp = event.lastTimestamp
      ? new Date(event.lastTimestamp as unknown as string).toISOString()
      : new Date().toISOString();

    const count = event.count && event.count >= 1 ? event.count : 1;

    return { type, reason, message, sourceComponent, firstTimestamp, lastTimestamp, count };
  });
}

/**
 * Handler for the get_events MCP tool.
 *
 * Validates input parameters, queries the K8s API (or returns mock data),
 * and returns a JSON-stringified events array.
 *
 * @param args - Record containing podName and namespace
 * @returns JSON-stringified EventEntry array
 * @throws McpToolError with appropriate error code on failure
 */
export async function handleGetEvents(args: Record<string, unknown>): Promise<string> {
  const podName = validatePodName(args.podName);
  const namespace = validateNamespace(args.namespace);

  const mode = process.env.MCP_MODE || 'live';

  if (mode === 'mock') {
    const mockEvents = getMockEvents();
    return JSON.stringify(mockEvents);
  }

  const maxEvents = resolveMaxEvents();

  try {
    const events = await Promise.race([
      fetchEventsFromK8s(podName, namespace, maxEvents),
      new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('K8S_TIMEOUT')), K8S_TIMEOUT_MS)
      ),
    ]);

    return JSON.stringify(events);
  } catch (error: unknown) {
    if (error instanceof McpToolError) throw error;

    const err = error as Error & { code?: string; statusCode?: number };

    // Timeout
    if (
      err.message === 'K8S_TIMEOUT' ||
      err.name === 'AbortError' ||
      err.message.includes('ETIMEDOUT')
    ) {
      throw new McpToolError(
        MCP_ERRORS.TIMEOUT,
        `Timeout: Kubernetes API did not respond within ${K8S_TIMEOUT_MS / 1000} seconds for get_events(pod=${podName}, namespace=${namespace})`
      );
    }

    // K8s API unreachable
    if (
      err.code === 'ECONNREFUSED' ||
      err.code === 'ENOTFOUND' ||
      err.code === 'ECONNRESET'
    ) {
      throw new McpToolError(
        MCP_ERRORS.UPSTREAM_FAILURE,
        `Kubernetes API unreachable: cluster communication failed for get_events(pod=${podName}, namespace=${namespace})`
      );
    }

    // Generic upstream failure
    throw new McpToolError(
      MCP_ERRORS.UPSTREAM_FAILURE,
      `Upstream failure: unable to query events — ${err.message || 'unknown error'}`
    );
  }
}
