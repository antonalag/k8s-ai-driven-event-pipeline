import { z } from 'zod';
import { type WriteToolResult, MCP_ERRORS, JSON_RPC_ERRORS } from '../types.js';
import { McpToolError } from './describe-pod.js';

/**
 * Zod schema for scale_deployment tool parameters.
 * Strict mode rejects any unexpected fields.
 * Replicas bounded to [0, 10] for safety.
 */
export const ScaleDeploymentSchema = z.object({
  deploymentName: z.string().min(1).max(253),
  namespace: z.string().min(1).max(63),
  replicas: z.number().int().min(0).max(10),
  correlationId: z.string().uuid().optional(),
}).strict();

export type ScaleDeploymentParams = z.infer<typeof ScaleDeploymentSchema>;

/**
 * Returns a mock success result for scale_deployment.
 */
function getMockResult(params: ScaleDeploymentParams): WriteToolResult {
  return {
    action: 'scale_deployment',
    status: 'completed',
    deploymentName: params.deploymentName,
    namespace: params.namespace,
    timestamp: new Date().toISOString(),
    details: {
      replicas: params.replicas,
      previousReplicas: 1,
    },
  };
}

/**
 * Executes a deployment scale operation via the Kubernetes API.
 */
async function executeLiveScale(params: ScaleDeploymentParams): Promise<WriteToolResult> {
  try {
    const { KubeConfig, AppsV1Api } = await import('@kubernetes/client-node');
    const kc = new KubeConfig();
    kc.loadFromDefault();

    const appsApi = kc.makeApiClient(AppsV1Api);
    const timestamp = new Date().toISOString();

    // Read current replicas for audit
    const current = await appsApi.readNamespacedDeployment({
      name: params.deploymentName,
      namespace: params.namespace,
    });
    const previousReplicas = current.spec?.replicas ?? 1;

    // Patch replicas
    const patch = {
      spec: {
        replicas: params.replicas,
      },
    };

    await appsApi.patchNamespacedDeployment({
      name: params.deploymentName,
      namespace: params.namespace,
      body: patch,
      fieldManager: 'mcp-server',
    });

    return {
      action: 'scale_deployment',
      status: 'completed',
      deploymentName: params.deploymentName,
      namespace: params.namespace,
      timestamp,
      details: {
        replicas: params.replicas,
        previousReplicas,
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
        `Kubernetes API unreachable: cannot scale deployment '${params.deploymentName}' in namespace '${params.namespace}'`
      );
    }

    throw new McpToolError(
      MCP_ERRORS.UPSTREAM_FAILURE,
      `Kubernetes API error: ${error.message || 'unknown error'} for deployment '${params.deploymentName}'`
    );
  }
}

/**
 * Handler for the scale_deployment MCP write-back tool.
 *
 * @param args - Raw arguments from JSON-RPC request.
 * @returns JSON-stringified WriteToolResult.
 * @throws McpToolError with appropriate error code on failure.
 */
export async function handleScaleDeployment(args: Record<string, unknown>): Promise<string> {
  const parseResult = ScaleDeploymentSchema.safeParse(args);
  if (!parseResult.success) {
    const issues = parseResult.error.issues.map(i => `${i.path.join('.')}: ${i.message}`).join('; ');
    throw new McpToolError(
      JSON_RPC_ERRORS.INVALID_PARAMS,
      `Invalid params for scale_deployment: ${issues}`
    );
  }

  const params = parseResult.data;
  const mode = process.env.MCP_MODE || 'live';

  let result: WriteToolResult;
  if (mode === 'mock') {
    result = getMockResult(params);
  } else {
    result = await executeLiveScale(params);
  }

  return JSON.stringify(result);
}
