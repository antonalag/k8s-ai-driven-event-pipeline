import { handleScaleDeployment, ScaleDeploymentSchema } from '../tools/scale-deployment';
import { McpToolError } from '../tools/describe-pod';
import { JSON_RPC_ERRORS } from '../types';

describe('scale_deployment tool', () => {
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
    it('should accept valid parameters with replicas in range', () => {
      const result = ScaleDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: 3,
      });
      expect(result.success).toBe(true);
    });

    it('should accept replicas = 0 (scale to zero)', () => {
      const result = ScaleDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: 0,
      });
      expect(result.success).toBe(true);
    });

    it('should accept replicas = 10 (maximum)', () => {
      const result = ScaleDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: 10,
      });
      expect(result.success).toBe(true);
    });

    it('should reject replicas > 10', () => {
      const result = ScaleDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: 11,
      });
      expect(result.success).toBe(false);
    });

    it('should reject replicas < 0', () => {
      const result = ScaleDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: -1,
      });
      expect(result.success).toBe(false);
    });

    it('should reject non-integer replicas', () => {
      const result = ScaleDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: 2.5,
      });
      expect(result.success).toBe(false);
    });

    it('should reject missing replicas', () => {
      const result = ScaleDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
      });
      expect(result.success).toBe(false);
    });

    it('should accept optional correlationId', () => {
      const result = ScaleDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: 2,
        correlationId: '550e8400-e29b-41d4-a716-446655440000',
      });
      expect(result.success).toBe(true);
    });

    it('should reject extra fields (strict mode)', () => {
      const result = ScaleDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: 2,
        injected: true,
      });
      expect(result.success).toBe(false);
    });

    it('should reject string replicas', () => {
      const result = ScaleDeploymentSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: '3',
      });
      expect(result.success).toBe(false);
    });
  });

  describe('mock mode execution', () => {
    it('should return a valid WriteToolResult on success', async () => {
      const resultStr = await handleScaleDeployment({
        deploymentName: 'chaos-app',
        namespace: 'chaos-validation',
        replicas: 3,
      });

      const result = JSON.parse(resultStr);
      expect(result.action).toBe('scale_deployment');
      expect(result.status).toBe('completed');
      expect(result.deploymentName).toBe('chaos-app');
      expect(result.namespace).toBe('chaos-validation');
      expect(result.details.replicas).toBe(3);
      expect(result.details.previousReplicas).toBe(1);
      expect(result.timestamp).toBeDefined();
    });

    it('should handle scale to zero', async () => {
      const resultStr = await handleScaleDeployment({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: 0,
      });

      const result = JSON.parse(resultStr);
      expect(result.status).toBe('completed');
      expect(result.details.replicas).toBe(0);
    });

    it('should handle scale to max (10)', async () => {
      const resultStr = await handleScaleDeployment({
        deploymentName: 'my-app',
        namespace: 'default',
        replicas: 10,
      });

      const result = JSON.parse(resultStr);
      expect(result.status).toBe('completed');
      expect(result.details.replicas).toBe(10);
    });
  });

  describe('parameter validation errors', () => {
    it('should throw INVALID_PARAMS for replicas out of bounds', async () => {
      try {
        await handleScaleDeployment({
          deploymentName: 'my-app',
          namespace: 'default',
          replicas: 15,
        });
        fail('Should have thrown');
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(JSON_RPC_ERRORS.INVALID_PARAMS);
        expect(error.message).toContain('scale_deployment');
      }
    });

    it('should throw INVALID_PARAMS for missing required fields', async () => {
      try {
        await handleScaleDeployment({ deploymentName: 'my-app' });
        fail('Should have thrown');
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(JSON_RPC_ERRORS.INVALID_PARAMS);
      }
    });

    it('should throw INVALID_PARAMS for extra fields', async () => {
      try {
        await handleScaleDeployment({
          deploymentName: 'my-app',
          namespace: 'default',
          replicas: 2,
          malicious: 'payload',
        });
        fail('Should have thrown');
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(JSON_RPC_ERRORS.INVALID_PARAMS);
      }
    });
  });
});
