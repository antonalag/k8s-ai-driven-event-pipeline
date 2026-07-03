import { z } from 'zod';
import { type WriteToolResult, MCP_ERRORS, JSON_RPC_ERRORS } from '../types.js';
import { McpToolError } from './describe-pod.js';

/**
 * Container image reference validation pattern.
 * Allows: registry/repo:tag, repo:tag, repo@sha256:digest
 * Prevents injection of invalid characters.
 */
const IMAGE_PATTERN = /^[a-z0-9]([a-z0-9._\-/]*[a-z0-9])?(:[a-zA-Z0-9][\w.\-]{0,127})?(@sha256:[a-f0-9]{64})?$/;

/**
 * Zod schema for fix_container_image tool parameters.
 * Strict mode rejects any unexpected fields.
 */
export const FixContainerImageSchema = z.object({
  deploymentName: z.string().min(1).max(253),
  namespace: z.string().min(1).max(63),
  containerName: z.string().min(1).max(63),
  correctImage: z.string().min(1).max(512).regex(IMAGE_PATTERN, {
    message: 'Invalid container image reference format',
  }),
  correlationId: z.string().uuid().optional(),
}).strict();

export type FixContainerImageParams = z.infer<typeof FixContainerImageSchema>;

/**
 * Returns a mock success result for fix_container_image.
 */
function getMockResult(params: FixContainerImageParams): WriteToolResult {
  return {
    action: 'fix_container_image',
    status: 'completed',
    deploymentName: params.deploymentName,
    namespace: params.namespace,
    timestamp: new Date().toISOString(),
    details: {
      containerName: params.containerName,
      newImage: params.correctImage,
      previousImage: 'invalid-registry.example.com/nonexistent:v0.0.0',
    },
  };
}

/**
 * Patches the container image in a deployment via the Kubernetes API.
 * Uses JSON Patch (RFC 6902) — the default patch format of @kubernetes/client-node.
 */
async function executeLiveFixImage(params: FixContainerImageParams): Promise<WriteToolResult> {
  try {
    const { KubeConfig, AppsV1Api } = await import('@kubernetes/client-node');
    const kc = new KubeConfig();
    kc.loadFromDefault();

    const appsApi = kc.makeApiClient(AppsV1Api);
    const timestamp = new Date().toISOString();

    // Read current deployment to validate container exists and find its index
    const deployment = await appsApi.readNamespacedDeployment({
      name: params.deploymentName,
      namespace: params.namespace,
    });

    const containers = deployment.spec?.template?.spec?.containers || [];
    const containerNames = containers.map(c => c.name);
    const containerIndex = containers.findIndex(c => c.name === params.containerName);

    if (containerIndex === -1) {
      throw new McpToolError(
        JSON_RPC_ERRORS.INVALID_PARAMS,
        `Container '${params.containerName}' not found in deployment '${params.deploymentName}'. Available containers: ${containerNames.join(', ')}`
      );
    }

    const previousImage = containers[containerIndex].image || 'unknown';

    // JSON Patch array — @kubernetes/client-node sends application/json-patch+json by default
    const jsonPatch = [
      {
        op: 'replace' as const,
        path: `/spec/template/spec/containers/${containerIndex}/image`,
        value: params.correctImage,
      },
    ];

    await appsApi.patchNamespacedDeployment({
      name: params.deploymentName,
      namespace: params.namespace,
      body: jsonPatch as unknown as Record<string, unknown>,
    });

    return {
      action: 'fix_container_image',
      status: 'completed',
      deploymentName: params.deploymentName,
      namespace: params.namespace,
      timestamp,
      details: {
        containerName: params.containerName,
        newImage: params.correctImage,
        previousImage,
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
        `Kubernetes API unreachable: cannot fix image for deployment '${params.deploymentName}' in namespace '${params.namespace}'`
      );
    }

    throw new McpToolError(
      MCP_ERRORS.UPSTREAM_FAILURE,
      `Kubernetes API error: ${error.message || 'unknown error'} for deployment '${params.deploymentName}'`
    );
  }
}

/**
 * Handler for the fix_container_image MCP write-back tool.
 */
export async function handleFixContainerImage(args: Record<string, unknown>): Promise<string> {
  const parseResult = FixContainerImageSchema.safeParse(args);
  if (!parseResult.success) {
    const issues = parseResult.error.issues.map(i => `${i.path.join('.')}: ${i.message}`).join('; ');
    throw new McpToolError(
      JSON_RPC_ERRORS.INVALID_PARAMS,
      `Invalid params for fix_container_image: ${issues}`
    );
  }

  const params = parseResult.data;
  const mode = process.env.MCP_MODE || 'live';

  let result: WriteToolResult;
  if (mode === 'mock') {
    result = getMockResult(params);
  } else {
    result = await executeLiveFixImage(params);
  }

  return JSON.stringify(result);
}
