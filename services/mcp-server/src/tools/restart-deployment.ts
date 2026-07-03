import { z } from 'zod';
import { type WriteToolResult, MCP_ERRORS, JSON_RPC_ERRORS } from '../types.js';
import { McpToolError } from './describe-pod.js';

/**
 * Zod schema for restart_deployment tool parameters.
 * Strict mode rejects any unexpected fields.
 */
export const RestartDeploymentSchema = z.object({
  deploymentName: z.string().min(1).max(253),
  namespace: z.string().min(1).max(63),
  correlationId: z.string().uuid().optional(),
}).strict();

export type RestartDeploymentParams = z.infer<typeof RestartDeploymentSchema>;

/**
 * Returns a mock success result for restart_deployment.
 */
function getMockResult(params: RestartDeploymentParams): WriteToolResult {
  return {
    action: 'restart_deployment',
    status: 'completed',
    deploymentName: params.deploymentName,
    namespace: params.namespace,
    timestamp: new Date().toISOString(),
    details: {
      annotation: 'kubectl.kubernetes.io/restartedAt',
      strategy: 'rolling-restart',
    },
  };
}

/**
 * Executes a rolling restart by patching the restart annotation.
 * Uses JSON Patch (RFC 6902) — the default patch format of @kubernetes/client-node.
 */
async function executeLiveRestart(params: RestartDeploymentParams): Promise<WriteToolResult> {
  try {
    const { KubeConfig, AppsV1Api } = await import('@kubernetes/client-node');
    const kc = new KubeConfig();
    kc.loadFromDefault();

    const appsApi = kc.makeApiClient(AppsV1Api);
    const restartedAt = new Date().toISOString();

    // Ensure annotations path exists by reading current state
    const deployment = await appsApi.readNamespacedDeployment({
      name: params.deploymentName,
      namespace: params.namespace,
    });

    const hasAnnotations = deployment.spec?.template?.metadata?.annotations !== undefined;

    // Build JSON Patch — use 'add' if annotations don't exist, 'replace' if they do
    const jsonPatch = hasAnnotations
      ? [{ op: 'add' as const, path: '/spec/template/metadata/annotations/kubectl.kubernetes.io~1restartedAt', value: restartedAt }]
      : [
          { op: 'add' as const, path: '/spec/template/metadata/annotations', value: { 'kubectl.kubernetes.io/restartedAt': restartedAt } },
        ];

    await appsApi.patchNamespacedDeployment({
      name: params.deploymentName,
      namespace: params.namespace,
      body: jsonPatch as unknown as Record<string, unknown>,
    });

    return {
      action: 'restart_deployment',
      status: 'completed',
      deploymentName: params.deploymentName,
      namespace: params.namespace,
      timestamp: restartedAt,
      details: {
        annotation: 'kubectl.kubernetes.io/restartedAt',
        strategy: 'rolling-restart',
      },
    };
  } catch (err: unknown) {
    if (err instanceof McpToolError) throw err;

    const error = err as { statusCode?: number; body?: { message?: string }; code?: string; message?: string };

    if (error.statusCode === 404 || error.body?.message?.includes('not found')) {
      throw new McpToolError(
        MCP_ERRORS.RESOURCE_NOT_FOUND,
        `Deployment '${params.deploymentName}' not found in namespace '${params.namespace}'`
      );
    }

    if (error.code === 'ECONNREFUSED' || error.code === 'ENOTFOUND' || error.code === 'ETIMEDOUT') {
      throw new McpToolError(
        MCP_ERRORS.UPSTREAM_FAILURE,
        `Kubernetes API unreachable: cannot restart deployment '${params.deploymentName}' in namespace '${params.namespace}'`
      );
    }

    throw new McpToolError(
      MCP_ERRORS.UPSTREAM_FAILURE,
      `Kubernetes API error: ${error.message || 'unknown error'} for deployment '${params.deploymentName}'`
    );
  }
}

/**
 * Handler for the restart_deployment MCP write-back tool.
 */
export async function handleRestartDeployment(args: Record<string, unknown>): Promise<string> {
  const parseResult = RestartDeploymentSchema.safeParse(args);
  if (!parseResult.success) {
    const issues = parseResult.error.issues.map(i => `${i.path.join('.')}: ${i.message}`).join('; ');
    throw new McpToolError(
      JSON_RPC_ERRORS.INVALID_PARAMS,
      `Invalid params for restart_deployment: ${issues}`
    );
  }

  const params = parseResult.data;
  const mode = process.env.MCP_MODE || 'live';

  let result: WriteToolResult;
  if (mode === 'mock') {
    result = getMockResult(params);
  } else {
    result = await executeLiveRestart(params);
  }

  return JSON.stringify(result);
}
