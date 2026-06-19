import { handleRestartDeployment, RestartDeploymentSchema } from '../tools/restart-deployment';
import { McpToolError } from '../tools/describe-pod';
import { JSON_RPC_ERRORS, MCP_ERRORS } from '../types';

describe('restart_deployment tool', () => {
  const originalMode = process.env.MCP_MODE;

  beforeEach(() => {
    process.env.MCP_MODE = 'mock';
  });

  afterEach(() => {
    if (originalMode === undefined) {
      delete process.env.MCP_MODE;
    } else {
      process.env.MCP_MODE = originalMode;
    }
  });

  describe('Zod schema validation', () => {
    it('should accept valid parameters', () => {
      const result = RestartDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
      });
      expect(result.success).toBe(true);
    });

    it('should accept valid parameters with correlationId', () => {
      const result = RestartDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        correlationId: '550e8400-e29b-41d4-a716-446655440000',
      });
      expect(result.success).toBe(true);
    });

    it('should reject empty deploymentName', () => {
      const result = RestartDeploymentSchema.safeParse({
        deploymentName: '',
        namespace: 'default',
      });
      expect(result.success).toBe(false);
    });

    it('should reject deploymentName exceeding 253 characters', () => {
      const result = RestartDeploymentSchema.safeParse({
        deploymentName: 'a'.repeat(254),
        namespace: 'default',
      });
      expect(result.success).toBe(false);
    });

    it('should reject empty namespace', () => {
      const result = RestartDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: '',
      });
      expect(result.success).toBe(false);
    });

    it('should reject namespace exceeding 63 characters', () => {
      const result = RestartDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'a'.repeat(64),
      });
      expect(result.success).toBe(false);
    });

    it('should reject invalid correlationId format', () => {
      const result = RestartDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        correlationId: 'not-a-uuid',
      });
      expect(result.success).toBe(false);
    });

    it('should reject unexpected extra fields (strict mode)', () => {
      const result = RestartDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        malicious: 'injected-field',
      });
      expect(result.success).toBe(false);
    });
  });

  describe('mock mode execution', () => {
    it('should return a valid WriteToolResult on success', async () => {
      const resultStr = await handleRestartDeployment({
        deploymentName: 'chaos-crash',
        namespace: 'chaos-validation',
      });

      const result = JSON.parse(resultStr);
      expect(result.action).toBe('restart_deployment');
      expect(result.status).toBe('completed');
      expect(result.deploymentName).toBe('chaos-crash');
      expect(result.namespace).toBe('chaos-validation');
      expect(result.timestamp).toBeDefined();
      expect(result.details.annotation).toBe('kubectl.kubernetes.io/restartedAt');
      expect(result.details.strategy).toBe('rolling-restart');
    });

    it('should include ISO-8601 timestamp', async () => {
      const resultStr = await handleRestartDeployment({
        deploymentName: 'my-app',
        namespace: 'default',
      });

      const result = JSON.parse(resultStr);
      const parsedDate = new Date(result.timestamp);
      expect(parsedDate.toISOString()).toBe(result.timestamp);
    });
  });

  describe('parameter validation errors', () => {
    it('should throw INVALID_PARAMS for missing deploymentName', async () => {
      await expect(
        handleRestartDeployment({ namespace: 'default' })
      ).rejects.toThrow(McpToolError);

      try {
        await handleRestartDeployment({ namespace: 'default' });
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(JSON_RPC_ERRORS.INVALID_PARAMS);
        expect(error.message).toContain('restart_deployment');
      }
    });

    it('should throw INVALID_PARAMS for missing namespace', async () => {
      await expect(
        handleRestartDeployment({ deploymentName: 'my-app' })
      ).rejects.toThrow(McpToolError);

      try {
        await handleRestartDeployment({ deploymentName: 'my-app' });
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(JSON_RPC_ERRORS.INVALID_PARAMS);
        expect(error.message).toContain('namespace');
      }
    });

    it('should throw INVALID_PARAMS for extra fields', async () => {
      await expect(
        handleRestartDeployment({
          deploymentName: 'my-app',
          namespace: 'default',
          extraField: 'injected',
        })
      ).rejects.toThrow(McpToolError);

      try {
        await handleRestartDeployment({
          deploymentName: 'my-app',
          namespace: 'default',
          extraField: 'injected',
        });
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(JSON_RPC_ERRORS.INVALID_PARAMS);
      }
    });
  });
});
