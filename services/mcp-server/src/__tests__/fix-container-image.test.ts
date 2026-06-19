import { handleFixContainerImage, FixContainerImageSchema } from '../tools/fix-container-image';
import { McpToolError } from '../tools/describe-pod';
import { JSON_RPC_ERRORS } from '../types';

describe('fix_container_image tool', () => {
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
    it('should accept valid parameters with simple image', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'main',
        correctImage: 'nginx:1.27-alpine',
      });
      expect(result.success).toBe(true);
    });

    it('should accept valid image with registry path', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'sidecar',
        correctImage: 'gcr.io/my-project/my-image:v1.2.3',
      });
      expect(result.success).toBe(true);
    });

    it('should accept image with SHA-256 digest', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'main',
        correctImage: 'nginx@sha256:' + 'a'.repeat(64),
      });
      expect(result.success).toBe(true);
    });

    it('should accept image with tag and digest', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'main',
        correctImage: 'nginx:1.27@sha256:' + 'b'.repeat(64),
      });
      expect(result.success).toBe(true);
    });

    it('should reject empty correctImage', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'main',
        correctImage: '',
      });
      expect(result.success).toBe(false);
    });

    it('should reject correctImage exceeding 512 characters', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'main',
        correctImage: 'a'.repeat(513),
      });
      expect(result.success).toBe(false);
    });

    it('should reject correctImage with invalid characters (spaces)', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'main',
        correctImage: 'nginx image:latest',
      });
      expect(result.success).toBe(false);
    });

    it('should reject correctImage starting with uppercase', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'main',
        correctImage: 'Nginx:latest',
      });
      expect(result.success).toBe(false);
    });

    it('should reject empty containerName', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: '',
        correctImage: 'nginx:1.27',
      });
      expect(result.success).toBe(false);
    });

    it('should reject containerName exceeding 63 characters', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'c'.repeat(64),
        correctImage: 'nginx:1.27',
      });
      expect(result.success).toBe(false);
    });

    it('should accept optional correlationId', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'main',
        correctImage: 'nginx:1.27',
        correlationId: '550e8400-e29b-41d4-a716-446655440000',
      });
      expect(result.success).toBe(true);
    });

    it('should reject extra fields (strict mode)', () => {
      const result = FixContainerImageSchema.safeParse({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'main',
        correctImage: 'nginx:1.27',
        shellCommand: 'rm -rf /',
      });
      expect(result.success).toBe(false);
    });
  });

  describe('mock mode execution', () => {
    it('should return a valid WriteToolResult on success', async () => {
      const resultStr = await handleFixContainerImage({
        deploymentName: 'chaos-imagepull',
        namespace: 'chaos-validation',
        containerName: 'main',
        correctImage: 'nginx:1.27-alpine',
      });

      const result = JSON.parse(resultStr);
      expect(result.action).toBe('fix_container_image');
      expect(result.status).toBe('completed');
      expect(result.deploymentName).toBe('chaos-imagepull');
      expect(result.namespace).toBe('chaos-validation');
      expect(result.details.containerName).toBe('main');
      expect(result.details.newImage).toBe('nginx:1.27-alpine');
      expect(result.details.previousImage).toBeDefined();
      expect(result.timestamp).toBeDefined();
    });

    it('should include ISO-8601 timestamp', async () => {
      const resultStr = await handleFixContainerImage({
        deploymentName: 'my-app',
        namespace: 'default',
        containerName: 'sidecar',
        correctImage: 'envoyproxy/envoy:v1.30',
      });

      const result = JSON.parse(resultStr);
      const parsedDate = new Date(result.timestamp);
      expect(parsedDate.toISOString()).toBe(result.timestamp);
    });
  });

  describe('parameter validation errors', () => {
    it('should throw INVALID_PARAMS for missing containerName', async () => {
      try {
        await handleFixContainerImage({
          deploymentName: 'my-app',
          namespace: 'default',
          correctImage: 'nginx:1.27',
        });
        fail('Should have thrown');
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(JSON_RPC_ERRORS.INVALID_PARAMS);
        expect(error.message).toContain('fix_container_image');
      }
    });

    it('should throw INVALID_PARAMS for invalid image format', async () => {
      try {
        await handleFixContainerImage({
          deploymentName: 'my-app',
          namespace: 'default',
          containerName: 'main',
          correctImage: 'INVALID IMAGE!!',
        });
        fail('Should have thrown');
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(JSON_RPC_ERRORS.INVALID_PARAMS);
        expect(error.message).toContain('Invalid container image reference');
      }
    });

    it('should throw INVALID_PARAMS for extra fields', async () => {
      try {
        await handleFixContainerImage({
          deploymentName: 'my-app',
          namespace: 'default',
          containerName: 'main',
          correctImage: 'nginx:1.27',
          extraField: 'injected',
        });
        fail('Should have thrown');
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(JSON_RPC_ERRORS.INVALID_PARAMS);
      }
    });
  });
});
