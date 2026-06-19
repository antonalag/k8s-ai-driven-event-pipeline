import { authorizeWriteOperation } from '../middleware/rbac-gate';
import { MCP_ERRORS } from '../types';
import { McpToolError } from '../tools/describe-pod';

describe('RBAC Simulation Gate', () => {
  const originalEnv = process.env.MCP_ALLOWED_NAMESPACES;

  afterEach(() => {
    if (originalEnv === undefined) {
      delete process.env.MCP_ALLOWED_NAMESPACES;
    } else {
      process.env.MCP_ALLOWED_NAMESPACES = originalEnv;
    }
  });

  describe('when MCP_ALLOWED_NAMESPACES is unset', () => {
    beforeEach(() => {
      delete process.env.MCP_ALLOWED_NAMESPACES;
    });

    it('should deny all write operations with FORBIDDEN error', () => {
      expect(() => authorizeWriteOperation('restart_deployment', 'default'))
        .toThrow(McpToolError);

      try {
        authorizeWriteOperation('restart_deployment', 'default');
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(MCP_ERRORS.FORBIDDEN);
        expect(error.message).toBe('No namespaces authorized for write operations');
      }
    });
  });

  describe('when MCP_ALLOWED_NAMESPACES is empty string', () => {
    beforeEach(() => {
      process.env.MCP_ALLOWED_NAMESPACES = '';
    });

    it('should deny all write operations', () => {
      expect(() => authorizeWriteOperation('scale_deployment', 'production'))
        .toThrow(McpToolError);

      try {
        authorizeWriteOperation('scale_deployment', 'production');
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(MCP_ERRORS.FORBIDDEN);
        expect(error.message).toBe('No namespaces authorized for write operations');
      }
    });
  });

  describe('when MCP_ALLOWED_NAMESPACES is whitespace only', () => {
    beforeEach(() => {
      process.env.MCP_ALLOWED_NAMESPACES = '   ';
    });

    it('should deny all write operations', () => {
      expect(() => authorizeWriteOperation('restart_deployment', 'default'))
        .toThrow(McpToolError);
    });
  });

  describe('when MCP_ALLOWED_NAMESPACES contains valid namespaces', () => {
    beforeEach(() => {
      process.env.MCP_ALLOWED_NAMESPACES = 'default,chaos-validation,staging';
    });

    it('should allow operations on authorized namespaces', () => {
      expect(() => authorizeWriteOperation('restart_deployment', 'default'))
        .not.toThrow();
      expect(() => authorizeWriteOperation('scale_deployment', 'chaos-validation'))
        .not.toThrow();
      expect(() => authorizeWriteOperation('fix_container_image', 'staging'))
        .not.toThrow();
    });

    it('should deny operations on unauthorized namespaces', () => {
      expect(() => authorizeWriteOperation('restart_deployment', 'production'))
        .toThrow(McpToolError);

      try {
        authorizeWriteOperation('restart_deployment', 'production');
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(MCP_ERRORS.FORBIDDEN);
        expect(error.message).toBe("Namespace 'production' is not authorized for write operations");
      }
    });

    it('should deny operations with non-write tool names', () => {
      expect(() => authorizeWriteOperation('describe_pod', 'default'))
        .toThrow(McpToolError);

      try {
        authorizeWriteOperation('describe_pod', 'default');
      } catch (err) {
        const error = err as McpToolError;
        expect(error.code).toBe(MCP_ERRORS.FORBIDDEN);
        expect(error.message).toContain("is not authorized for write operations");
      }
    });

    it('should deny operations with unknown tool names', () => {
      expect(() => authorizeWriteOperation('delete_pod', 'default'))
        .toThrow(McpToolError);
    });
  });

  describe('namespace allowlist parsing', () => {
    it('should trim whitespace from namespace entries', () => {
      process.env.MCP_ALLOWED_NAMESPACES = ' default , chaos-validation , staging ';
      expect(() => authorizeWriteOperation('restart_deployment', 'default'))
        .not.toThrow();
      expect(() => authorizeWriteOperation('restart_deployment', 'chaos-validation'))
        .not.toThrow();
    });

    it('should ignore empty entries from double commas', () => {
      process.env.MCP_ALLOWED_NAMESPACES = 'default,,chaos-validation';
      expect(() => authorizeWriteOperation('restart_deployment', 'default'))
        .not.toThrow();
      expect(() => authorizeWriteOperation('restart_deployment', 'chaos-validation'))
        .not.toThrow();
    });
  });

  describe('structured logging', () => {
    let consoleSpy: jest.SpyInstance;

    beforeEach(() => {
      consoleSpy = jest.spyOn(console, 'log').mockImplementation();
      process.env.MCP_ALLOWED_NAMESPACES = 'default,chaos-validation';
    });

    afterEach(() => {
      consoleSpy.mockRestore();
    });

    it('should log ALLOW decisions as structured JSON', () => {
      authorizeWriteOperation('restart_deployment', 'default');

      expect(consoleSpy).toHaveBeenCalledTimes(1);
      const logged = JSON.parse(consoleSpy.mock.calls[0][0]);
      expect(logged.tool).toBe('restart_deployment');
      expect(logged.namespace).toBe('default');
      expect(logged.decision).toBe('ALLOW');
      expect(logged.reason).toContain('allowlist');
      expect(logged.timestamp).toBeDefined();
    });

    it('should log DENY decisions as structured JSON', () => {
      try {
        authorizeWriteOperation('restart_deployment', 'production');
      } catch {
        // expected
      }

      expect(consoleSpy).toHaveBeenCalledTimes(1);
      const logged = JSON.parse(consoleSpy.mock.calls[0][0]);
      expect(logged.tool).toBe('restart_deployment');
      expect(logged.namespace).toBe('production');
      expect(logged.decision).toBe('DENY');
      expect(logged.reason).toContain('not authorized');
    });
  });
});
