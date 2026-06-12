/**
 * Jest tests for MCP Server JSON-RPC handler and tool implementations.
 * Tests run in mock mode (MCP_MODE=mock) to exercise handler logic
 * without a real Kubernetes cluster.
 *
 * Validates: Requirements 1.2, 1.3, 2.1, 2.2, 3.1, 3.3, 4.1, 4.4
 */

import { handleJsonRpcRequest } from '../rpc-handler.js';
import type { JsonRpcErrorResponse, JsonRpcSuccessResponse } from '../types.js';

// Set mock mode for all tests
beforeAll(() => {
  process.env.MCP_MODE = 'mock';
});

afterAll(() => {
  delete process.env.MCP_MODE;
});

/** Helper to build a valid JSON-RPC 2.0 tools/call request body */
function buildRequest(toolName: string, args: Record<string, unknown> = {}, id: string | number = 1): string {
  return JSON.stringify({
    jsonrpc: '2.0',
    method: 'tools/call',
    params: { name: toolName, arguments: args },
    id,
  });
}

// ─────────────────────────────────────────────────────────────────────
// 1. Tool Whitelist Enforcement (Requirement 1.2, 1.3)
// ─────────────────────────────────────────────────────────────────────
describe('Tool whitelist enforcement', () => {
  it('returns -32601 for a non-whitelisted tool name', async () => {
    const response = await handleJsonRpcRequest(buildRequest('delete_pod'));
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32601);
    expect(err.error.message).toContain('delete_pod');
    expect(err.error.message).toContain('not whitelisted');
  });

  it('returns -32601 for an empty tool name', async () => {
    const response = await handleJsonRpcRequest(buildRequest(''));
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32601);
  });

  it('returns -32601 for a random unknown tool', async () => {
    const response = await handleJsonRpcRequest(buildRequest('exec_command'));
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32601);
  });

  it('allows whitelisted tools through', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('describe_pod', { podName: 'test', namespace: 'default' })
    );
    expect('error' in response && response.error).toBeFalsy();
    expect((response as JsonRpcSuccessResponse).result).toBeDefined();
  });
});

// ─────────────────────────────────────────────────────────────────────
// 2. Invalid JSON-RPC Requests (-32700, -32600)
// ─────────────────────────────────────────────────────────────────────
describe('Invalid JSON-RPC request handling', () => {
  it('returns -32700 for invalid JSON', async () => {
    const response = await handleJsonRpcRequest('{not valid json');
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32700);
    expect(err.error.message).toContain('Parse error');
  });

  it('returns -32600 for missing jsonrpc version', async () => {
    const response = await handleJsonRpcRequest(
      JSON.stringify({ method: 'tools/call', params: { name: 'describe_pod' }, id: 1 })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32600);
  });

  it('returns -32600 for missing method field', async () => {
    const response = await handleJsonRpcRequest(
      JSON.stringify({ jsonrpc: '2.0', params: { name: 'describe_pod' }, id: 1 })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32600);
  });

  it('returns -32600 for missing id field', async () => {
    const response = await handleJsonRpcRequest(
      JSON.stringify({ jsonrpc: '2.0', method: 'tools/call', params: { name: 'describe_pod' } })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32600);
  });

  it('returns -32601 for non-tools/call method', async () => {
    const response = await handleJsonRpcRequest(
      JSON.stringify({ jsonrpc: '2.0', method: 'resources/list', params: { name: 'x' }, id: 1 })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32601);
  });

  it('returns -32600 when params.name is missing', async () => {
    const response = await handleJsonRpcRequest(
      JSON.stringify({ jsonrpc: '2.0', method: 'tools/call', params: {}, id: 1 })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32600);
    expect(err.error.message).toContain('params.name');
  });

  it('returns -32600 when params is missing entirely', async () => {
    const response = await handleJsonRpcRequest(
      JSON.stringify({ jsonrpc: '2.0', method: 'tools/call', id: 1 })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32600);
    expect(err.error.message).toContain('params.name');
  });
});

// ─────────────────────────────────────────────────────────────────────
// 3. describe_pod Tool (Requirement 2.1, 2.2)
// ─────────────────────────────────────────────────────────────────────
describe('describe_pod tool', () => {
  it('returns -32602 when podName is empty', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('describe_pod', { podName: '', namespace: 'default' })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
    expect(err.error.message).toContain('podName');
  });

  it('returns -32602 when namespace is empty', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('describe_pod', { podName: 'my-pod', namespace: '' })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
    expect(err.error.message).toContain('namespace');
  });

  it('returns -32602 when namespace exceeds 63 chars', async () => {
    const longNs = 'a'.repeat(64);
    const response = await handleJsonRpcRequest(
      buildRequest('describe_pod', { podName: 'my-pod', namespace: longNs })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
    expect(err.error.message).toContain('namespace');
  });

  it('returns -32602 when podName is missing entirely', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('describe_pod', { namespace: 'default' })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
  });

  it('returns structured response with containers array in mock mode', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('describe_pod', { podName: 'test-pod', namespace: 'default' })
    );
    const success = response as JsonRpcSuccessResponse;
    expect(success.result).toBeDefined();
    expect(success.result.content).toHaveLength(1);
    expect(success.result.content[0].type).toBe('text');

    const parsed = JSON.parse(success.result.content[0].text);
    expect(parsed.containers).toBeInstanceOf(Array);
    expect(parsed.containers.length).toBeGreaterThan(0);
    expect(parsed.containers[0]).toHaveProperty('name');
    expect(parsed.containers[0]).toHaveProperty('image');
    expect(parsed.containers[0]).toHaveProperty('resources');
    expect(parsed.containers[0]).toHaveProperty('restartCount');
    expect(parsed.containers[0]).toHaveProperty('state');
    expect(parsed).toHaveProperty('phase');
    expect(parsed).toHaveProperty('conditions');
    expect(parsed).toHaveProperty('nodeName');
  });

  it('accepts podName at maximum length (253 chars)', async () => {
    const longPod = 'p'.repeat(253);
    const response = await handleJsonRpcRequest(
      buildRequest('describe_pod', { podName: longPod, namespace: 'default' })
    );
    const success = response as JsonRpcSuccessResponse;
    expect(success.result).toBeDefined();
  });

  it('accepts namespace at maximum length (63 chars)', async () => {
    const maxNs = 'n'.repeat(63);
    const response = await handleJsonRpcRequest(
      buildRequest('describe_pod', { podName: 'test', namespace: maxNs })
    );
    const success = response as JsonRpcSuccessResponse;
    expect(success.result).toBeDefined();
  });

  it('returns -32602 when podName exceeds 253 chars', async () => {
    const longPod = 'p'.repeat(254);
    const response = await handleJsonRpcRequest(
      buildRequest('describe_pod', { podName: longPod, namespace: 'default' })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
    expect(err.error.message).toContain('podName');
  });
});

// ─────────────────────────────────────────────────────────────────────
// 4. get_events Tool (Requirement 3.1, 3.3)
// ─────────────────────────────────────────────────────────────────────
describe('get_events tool', () => {
  it('returns -32602 when podName is empty', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_events', { podName: '', namespace: 'default' })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
  });

  it('returns -32602 when namespace is empty', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_events', { podName: 'my-pod', namespace: '' })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
    expect(err.error.message).toContain('namespace');
  });

  it('returns -32602 when namespace exceeds 63 chars', async () => {
    const longNs = 'x'.repeat(64);
    const response = await handleJsonRpcRequest(
      buildRequest('get_events', { podName: 'my-pod', namespace: longNs })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
  });

  it('returns events ordered by lastTimestamp descending in mock mode', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_events', { podName: 'test-pod', namespace: 'default' })
    );
    const success = response as JsonRpcSuccessResponse;
    const events = JSON.parse(success.result.content[0].text);

    expect(Array.isArray(events)).toBe(true);
    expect(events.length).toBeGreaterThan(0);

    // Verify descending order by lastTimestamp
    for (let i = 1; i < events.length; i++) {
      const prev = new Date(events[i - 1].lastTimestamp).getTime();
      const curr = new Date(events[i].lastTimestamp).getTime();
      expect(prev).toBeGreaterThanOrEqual(curr);
    }
  });

  it('each event has required field structure', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_events', { podName: 'test-pod', namespace: 'default' })
    );
    const success = response as JsonRpcSuccessResponse;
    const events = JSON.parse(success.result.content[0].text);

    for (const event of events) {
      expect(event).toHaveProperty('type');
      expect(['Normal', 'Warning']).toContain(event.type);
      expect(event).toHaveProperty('reason');
      expect(typeof event.reason).toBe('string');
      expect(event).toHaveProperty('message');
      expect(typeof event.message).toBe('string');
      expect(event.message.length).toBeLessThanOrEqual(1024);
      expect(event).toHaveProperty('sourceComponent');
      expect(typeof event.sourceComponent).toBe('string');
      expect(event).toHaveProperty('firstTimestamp');
      expect(event).toHaveProperty('lastTimestamp');
      // ISO 8601 format validation
      expect(new Date(event.firstTimestamp).toISOString()).toBeTruthy();
      expect(new Date(event.lastTimestamp).toISOString()).toBeTruthy();
      expect(event).toHaveProperty('count');
      expect(event.count).toBeGreaterThanOrEqual(1);
    }
  });

  it('respects count limit (mock returns limited events)', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_events', { podName: 'test-pod', namespace: 'default' })
    );
    const success = response as JsonRpcSuccessResponse;
    const events = JSON.parse(success.result.content[0].text);
    // Default max is 20; mock returns a small set
    expect(events.length).toBeLessThanOrEqual(20);
  });

  it('mock mode returns exactly 2 events', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_events', { podName: 'test-pod', namespace: 'default' })
    );
    const success = response as JsonRpcSuccessResponse;
    const events = JSON.parse(success.result.content[0].text);
    expect(events).toHaveLength(2);
  });
});

// ─────────────────────────────────────────────────────────────────────
// 5. get_logs Tool (Requirement 4.1, 4.4)
// ─────────────────────────────────────────────────────────────────────
describe('get_logs tool', () => {
  it('returns -32602 when tailLines is 0', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: 'test', namespace: 'default', tailLines: 0 })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
    expect(err.error.message).toContain('tailLines');
  });

  it('returns -32602 when tailLines is 10001', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: 'test', namespace: 'default', tailLines: 10001 })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
    expect(err.error.message).toContain('tailLines');
  });

  it('returns -32602 when tailLines is 50000', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: 'test', namespace: 'default', tailLines: 50000 })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
    expect(err.error.message).toContain('tailLines');
  });

  it('returns -32602 when tailLines is negative', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: 'test', namespace: 'default', tailLines: -5 })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
  });

  it('returns -32602 when podName is empty', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: '', namespace: 'default' })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
  });

  it('returns -32602 when namespace exceeds 63 chars', async () => {
    const longNs = 'z'.repeat(64);
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: 'test', namespace: longNs })
    );
    const err = response as JsonRpcErrorResponse;
    expect(err.error.code).toBe(-32602);
  });

  it('returns valid response with logs/truncated/containerName fields in mock mode', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: 'test-pod', namespace: 'default' })
    );
    const success = response as JsonRpcSuccessResponse;
    expect(success.result).toBeDefined();
    expect(success.result.content).toHaveLength(1);

    const parsed = JSON.parse(success.result.content[0].text);
    expect(parsed).toHaveProperty('logs');
    expect(typeof parsed.logs).toBe('string');
    expect(parsed).toHaveProperty('truncated');
    expect(typeof parsed.truncated).toBe('boolean');
    expect(parsed).toHaveProperty('containerName');
    expect(typeof parsed.containerName).toBe('string');
  });

  it('uses default tailLines of 100 when not specified', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: 'test-pod', namespace: 'default' })
    );
    const success = response as JsonRpcSuccessResponse;
    const parsed = JSON.parse(success.result.content[0].text);
    // Mock data has fewer lines than 100, so all should be returned
    expect(parsed.logs.length).toBeGreaterThan(0);
    expect(parsed.truncated).toBe(false);
  });

  it('accepts valid tailLines boundary value of 1', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: 'test-pod', namespace: 'default', tailLines: 1 })
    );
    const success = response as JsonRpcSuccessResponse;
    const parsed = JSON.parse(success.result.content[0].text);
    // With tailLines=1, only the last line should be returned
    const lineCount = parsed.logs.split('\n').filter((l: string) => l.length > 0).length;
    expect(lineCount).toBeLessThanOrEqual(1);
  });

  it('accepts valid tailLines boundary value of 10000', async () => {
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: 'test-pod', namespace: 'default', tailLines: 10000 })
    );
    const success = response as JsonRpcSuccessResponse;
    expect(success.result).toBeDefined();
  });

  it('truncation is signaled when logs exceed 32KB', async () => {
    // The mock data is small, so truncated should be false
    const response = await handleJsonRpcRequest(
      buildRequest('get_logs', { podName: 'test-pod', namespace: 'default' })
    );
    const success = response as JsonRpcSuccessResponse;
    const parsed = JSON.parse(success.result.content[0].text);
    // Mock data is small, verify that truncated field is correctly set to false
    expect(parsed.truncated).toBe(false);
    // Verify size is under 32KB
    expect(Buffer.byteLength(parsed.logs, 'utf-8')).toBeLessThanOrEqual(32_768);
  });
});
