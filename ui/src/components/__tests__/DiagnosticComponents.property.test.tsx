import { describe, it, expect, afterEach } from 'vitest';
import { render, cleanup, within } from '@testing-library/react';
import * as fc from 'fast-check';
import { RecommendedActionBlock } from '../RecommendedActionBlock';
import { McpToolBadge } from '../McpToolBadge';
import { Rfc7807FieldLayout } from '../Rfc7807FieldLayout';
import { ActionGroupCollapsible, categorizeAction } from '../ActionGroupCollapsible';

afterEach(() => {
  cleanup();
});

/**
 * Validates: Requirements 9.1
 */
describe('Feature: e2e-validation-diagnostic-calibration, Property 10: UI renders N actions as N code blocks with copy buttons', () => {
  it('renders exactly N action blocks and N copy buttons for N actions', () => {
    const actionsArb = fc.integer({ min: 1, max: 5 }).map((count) =>
      Array.from({ length: count }, (_, i) => `kubectl get pod-${i}`)
    );

    fc.assert(
      fc.property(actionsArb, (actions) => {
        cleanup();
        const { container } = render(<RecommendedActionBlock actions={actions} />);

        for (let i = 0; i < actions.length; i++) {
          const block = container.querySelector(`[data-testid="action-block-${i}"]`);
          expect(block).not.toBeNull();
          const copyBtn = container.querySelector(`[data-testid="copy-button-${i}"]`);
          expect(copyBtn).not.toBeNull();
        }

        // Verify no extra blocks beyond N
        expect(container.querySelector(`[data-testid="action-block-${actions.length}"]`)).toBeNull();
        expect(container.querySelector(`[data-testid="copy-button-${actions.length}"]`)).toBeNull();
      }),
      { numRuns: 100 }
    );
  });
});

/**
 * Validates: Requirements 9.2
 */
describe('Feature: e2e-validation-diagnostic-calibration, Property 11: UI renders correct badge count for mcpToolsUsed', () => {
  it('renders exactly as many badges as tools in mcpToolsUsed', () => {
    const validTools = ['describe_pod', 'get_events', 'get_logs'] as const;
    const toolSubsetArb = fc.subarray([...validTools], { minLength: 1, maxLength: 3 });

    fc.assert(
      fc.property(toolSubsetArb, (tools) => {
        cleanup();
        const { container } = render(<McpToolBadge mcpToolsUsed={tools} />);

        const badgesContainer = container.querySelector('[data-testid="mcp-tool-badges"]');
        expect(badgesContainer).not.toBeNull();

        for (const tool of tools) {
          const badge = container.querySelector(`[data-testid="mcp-badge-${tool}"]`);
          expect(badge).not.toBeNull();
        }

        // Verify no badges for tools not in the list
        for (const tool of validTools) {
          if (!tools.includes(tool)) {
            expect(container.querySelector(`[data-testid="mcp-badge-${tool}"]`)).toBeNull();
          }
        }
      }),
      { numRuns: 100 }
    );
  });

  it('returns null when mcpToolsUsed is empty', () => {
    const { container } = render(<McpToolBadge mcpToolsUsed={[]} />);
    expect(container.innerHTML).toBe('');
  });

  it('returns null when mcpToolsUsed is undefined', () => {
    const { container } = render(<McpToolBadge mcpToolsUsed={undefined} />);
    expect(container.innerHTML).toBe('');
  });
});

/**
 * Validates: Requirements 9.4
 */
describe('Feature: e2e-validation-diagnostic-calibration, Property 12: UI renders all RFC 7807 fields in labeled layout', () => {
  it('renders a labeled row for each non-null RFC 7807 field', () => {
    const problemArb = fc.record({
      type: fc.option(fc.constant('https://example.com/problems/test'), { nil: undefined }),
      title: fc.option(fc.constantFrom('Not Found', 'Service Unavailable', 'Bad Request'), { nil: undefined }),
      status: fc.option(fc.constantFrom(400, 404, 500, 503), { nil: undefined }),
      detail: fc.option(fc.constantFrom('Something went wrong', 'Resource not found', 'Timeout exceeded'), { nil: undefined }),
    });

    fc.assert(
      fc.property(problemArb, (problem) => {
        cleanup();
        const { container } = render(<Rfc7807FieldLayout problem={problem} />);

        const fields: Array<{ key: keyof typeof problem; label: string }> = [
          { key: 'type', label: 'type' },
          { key: 'title', label: 'title' },
          { key: 'status', label: 'status' },
          { key: 'detail', label: 'detail' },
        ];

        for (const { key, label } of fields) {
          const fieldEl = container.querySelector(`[data-testid="rfc7807-field-${label}"]`);
          if (problem[key] != null) {
            expect(fieldEl).not.toBeNull();
          } else {
            expect(fieldEl).toBeNull();
          }
        }
      }),
      { numRuns: 100 }
    );
  });
});

/**
 * Validates: Requirements 9.5
 */
describe('Feature: e2e-validation-diagnostic-calibration, Property 13: UI action grouping by categorization', () => {
  it('categorizeAction correctly routes actions to their categories', () => {
    const kubectlArb = fc.nat({ max: 999 }).map((n) => `kubectl get resource-${n}`);
    const configArb = fc.constantFrom(
      'edit deployment',
      'config set-context',
      'set replicas to 3',
      'edit configmap',
      'config reload'
    );
    const verificationArb = fc.nat({ max: 999 }).map((n) => `verify health check ${n}`);

    fc.assert(
      fc.property(kubectlArb, (action) => {
        expect(categorizeAction(action)).toBe('kubectl-commands');
      }),
      { numRuns: 100 }
    );

    fc.assert(
      fc.property(configArb, (action) => {
        expect(categorizeAction(action)).toBe('configuration-changes');
      }),
      { numRuns: 100 }
    );

    fc.assert(
      fc.property(verificationArb, (action) => {
        expect(categorizeAction(action)).toBe('verification-steps');
      }),
      { numRuns: 100 }
    );
  });

  it('groups >5 actions into collapsible category sections', () => {
    const mixedActionsArb = fc.tuple(
      fc.nat({ max: 9 }),
      fc.nat({ max: 9 }),
      fc.nat({ max: 9 })
    ).map(([a, b, c]) => [
      `kubectl get pod-${a}`,
      `kubectl describe svc-${b}`,
      `edit config-${c}`,
      `set replicas`,
      `verify health`,
      `verify dns`,
    ]);

    fc.assert(
      fc.property(mixedActionsArb, (actions) => {
        cleanup();
        const { container } = render(<ActionGroupCollapsible actions={actions} />);

        const grouped: Record<string, string[]> = {
          'kubectl-commands': [],
          'configuration-changes': [],
          'verification-steps': [],
        };
        for (const action of actions) {
          grouped[categorizeAction(action)].push(action);
        }

        for (const [category, items] of Object.entries(grouped)) {
          if (items.length > 0) {
            const group = container.querySelector(`[data-testid="action-group-${category}"]`);
            expect(group).not.toBeNull();
          }
        }
      }),
      { numRuns: 100 }
    );
  });

  it('delegates to RecommendedActionBlock when actions <= 5', () => {
    fc.assert(
      fc.property(fc.integer({ min: 1, max: 5 }), (count) => {
        const actions = Array.from({ length: count }, (_, i) => `action-${i}`);
        cleanup();
        const { container } = render(<ActionGroupCollapsible actions={actions} />);

        // Should NOT render the collapsible container
        expect(container.querySelector('[data-testid="action-group-collapsible"]')).toBeNull();

        // Should render action blocks directly (delegated to RecommendedActionBlock)
        for (let i = 0; i < actions.length; i++) {
          const block = container.querySelector(`[data-testid="action-block-${i}"]`);
          expect(block).not.toBeNull();
        }
      }),
      { numRuns: 100 }
    );
  });
});
