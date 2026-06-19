/**
 * Feature: e2e-validation-diagnostic-calibration
 * Properties 10–13: UI Diagnostic Component Property Tests
 *
 * Property 10: UI renders N actions as N code blocks with copy buttons
 * Property 11: UI renders correct badge count for mcpToolsUsed
 * Property 12: UI renders all RFC 7807 fields in labeled layout
 * Property 13: UI action grouping by categorization
 *
 * Validates: Requirements 9.1, 9.2, 9.4, 9.5
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { render, cleanup } from '@testing-library/react';
import { RecommendedActionBlock } from '../components/RecommendedActionBlock';
import { McpToolBadge } from '../components/McpToolBadge';
import { Rfc7807FieldLayout } from '../components/Rfc7807FieldLayout';
import {
  ActionGroupCollapsible,
  categorizeAction,
} from '../components/ActionGroupCollapsible';

// --- Generators ---

const actionArb = fc.stringMatching(/^[a-zA-Z0-9 \-\/_.=]+$/).filter((s) => s.length >= 3 && s.length <= 200);

const kubectlActionArb = fc.string({ minLength: 3, maxLength: 100 }).map((s) => `kubectl ${s}`);

const configActionArb = fc.constantFrom(
  'edit deployment/app',
  'set image deployment/app container=image:v2',
  'config set memory-limit 256Mi',
);

const verificationActionArb = fc.string({ minLength: 5, maxLength: 100 }).filter(
  (s) => !s.startsWith('kubectl') && !/config|edit|set/i.test(s),
);

const mcpToolArb = fc.constantFrom('describe_pod', 'get_events', 'get_logs');

// --- Property 10: N actions → N code blocks with copy buttons ---

describe('Property 10: UI renders N actions as N code blocks with copy buttons', () => {
  it('for any list of 1-5 actions, renders exactly N action blocks each with a copy button', () => {
    fc.assert(
      fc.property(
        fc.array(actionArb.filter((s) => s.trim().length > 0), { minLength: 1, maxLength: 5 }),
        (actions: string[]) => {
          const { container } = render(<RecommendedActionBlock actions={actions} />);

          // Each action renders as a code block
          const actionBlocks = container.querySelectorAll('[data-testid^="action-block-"]');
          expect(actionBlocks.length).toBe(actions.length);

          // Each action block has a copy button
          const copyButtons = container.querySelectorAll('[data-testid^="copy-button-"]');
          expect(copyButtons.length).toBe(actions.length);

          // Each code block contains the correct action text
          actions.forEach((action, idx) => {
            const block = container.querySelector(`[data-testid="action-block-${idx}"]`);
            expect(block).not.toBeNull();
            expect(block!.textContent).toContain(action);
          });

          cleanup();
        },
      ),
      { numRuns: 100 },
    );
  });

  it('renders nothing when actions array is empty', () => {
    const { container } = render(<RecommendedActionBlock actions={[]} />);
    const actionBlocks = container.querySelectorAll('[data-testid^="action-block-"]');
    expect(actionBlocks.length).toBe(0);
    cleanup();
  });
});

// --- Property 11: Correct badge count for mcpToolsUsed ---

describe('Property 11: UI renders correct badge count for mcpToolsUsed', () => {
  it('for any subset of MCP tools (0-3), renders exactly N badges', () => {
    fc.assert(
      fc.property(
        mcpToolArb.filter((s) => s.length > 0).chain(() =>
          fc.uniqueArray(mcpToolArb, { minLength: 0, maxLength: 3 }),
        ),
        (tools: string[]) => {
          const { container } = render(<McpToolBadge mcpToolsUsed={tools} />);

          if (tools.length === 0) {
            // Component returns null for empty tools
            const badges = container.querySelectorAll('[data-testid^="mcp-badge-"]');
            expect(badges.length).toBe(0);
          } else {
            const badges = container.querySelectorAll('[data-testid^="mcp-badge-"]');
            expect(badges.length).toBe(tools.length);

            // Each badge displays the tool name
            tools.forEach((tool) => {
              const badge = container.querySelector(`[data-testid="mcp-badge-${tool}"]`);
              expect(badge).not.toBeNull();
              expect(badge!.textContent).toContain(tool);
            });
          }

          cleanup();
        },
      ),
      { numRuns: 100 },
    );
  });

  it('hides badge section entirely when mcpToolsUsed is undefined', () => {
    const { container } = render(<McpToolBadge mcpToolsUsed={undefined} />);
    const badgeContainer = container.querySelector('[data-testid="mcp-tool-badges"]');
    expect(badgeContainer).toBeNull();
    cleanup();
  });
});

// --- Property 12: RFC 7807 fields rendered in labeled layout ---

describe('Property 12: UI renders all RFC 7807 fields in labeled layout', () => {
  it('for any ProblemDetail with all fields present, renders type/title/status/detail in labeled rows', () => {
    fc.assert(
      fc.property(
        fc.record({
          type: fc.webUrl(),
          title: fc.string({ minLength: 1, maxLength: 100 }),
          status: fc.integer({ min: 100, max: 599 }),
          detail: fc.string({ minLength: 1, maxLength: 300 }),
        }),
        (problem) => {
          const { container } = render(<Rfc7807FieldLayout problem={problem} />);

          const layout = container.querySelector('[data-testid="rfc7807-layout"]');
          expect(layout).not.toBeNull();

          // Type field
          const typeField = container.querySelector('[data-testid="rfc7807-field-type"]');
          expect(typeField).not.toBeNull();
          expect(typeField!.textContent).toContain(problem.type);

          // Title field
          const titleField = container.querySelector('[data-testid="rfc7807-field-title"]');
          expect(titleField).not.toBeNull();
          expect(titleField!.textContent).toContain(problem.title);

          // Status field
          const statusField = container.querySelector('[data-testid="rfc7807-field-status"]');
          expect(statusField).not.toBeNull();
          expect(statusField!.textContent).toContain(String(problem.status));

          // Detail field
          const detailField = container.querySelector('[data-testid="rfc7807-field-detail"]');
          expect(detailField).not.toBeNull();
          expect(detailField!.textContent).toContain(problem.detail);

          cleanup();
        },
      ),
      { numRuns: 100 },
    );
  });

  it('omits fields that are undefined', () => {
    const { container } = render(<Rfc7807FieldLayout problem={{}} />);
    const layout = container.querySelector('[data-testid="rfc7807-layout"]');
    expect(layout).not.toBeNull();
    // No field rows should render for undefined values
    const fields = container.querySelectorAll('[data-testid^="rfc7807-field-"]');
    expect(fields.length).toBe(0);
    cleanup();
  });
});

// --- Property 13: UI action grouping by categorization ---

describe('Property 13: UI action grouping by categorization', () => {
  it('categorizeAction correctly routes kubectl commands to kubectl-commands category', () => {
    fc.assert(
      fc.property(kubectlActionArb, (action: string) => {
        expect(categorizeAction(action)).toBe('kubectl-commands');
      }),
      { numRuns: 100 },
    );
  });

  it('categorizeAction correctly routes config/edit/set actions to configuration-changes category', () => {
    fc.assert(
      fc.property(configActionArb, (action: string) => {
        expect(categorizeAction(action)).toBe('configuration-changes');
      }),
      { numRuns: 50 },
    );
  });

  it('categorizeAction routes unmatched actions to verification-steps category', () => {
    fc.assert(
      fc.property(verificationActionArb, (action: string) => {
        expect(categorizeAction(action)).toBe('verification-steps');
      }),
      { numRuns: 100 },
    );
  });

  it('for >5 actions, renders collapsible groups with correct categorization', () => {
    // Generate at least 6 mixed actions
    const mixedActionsArb = fc.tuple(
      fc.array(kubectlActionArb, { minLength: 2, maxLength: 3 }),
      fc.array(configActionArb, { minLength: 2, maxLength: 3 }),
      fc.array(verificationActionArb, { minLength: 2, maxLength: 3 }),
    ).map(([kubectl, config, verification]) => [...kubectl, ...config, ...verification]);

    fc.assert(
      fc.property(mixedActionsArb, (actions: string[]) => {
        if (actions.length <= 5) return; // Skip if not enough actions

        const { container } = render(<ActionGroupCollapsible actions={actions} />);

        const collapsible = container.querySelector('[data-testid="action-group-collapsible"]');
        expect(collapsible).not.toBeNull();

        // Count kubectl actions
        const kubectlCount = actions.filter((a) => a.startsWith('kubectl')).length;
        if (kubectlCount > 0) {
          const group = container.querySelector('[data-testid="action-group-kubectl-commands"]');
          expect(group).not.toBeNull();
        }

        // Count config actions
        const configCount = actions.filter((a) => /config|edit|set/i.test(a) && !a.startsWith('kubectl')).length;
        if (configCount > 0) {
          const group = container.querySelector('[data-testid="action-group-configuration-changes"]');
          expect(group).not.toBeNull();
        }

        cleanup();
      }),
      { numRuns: 50 },
    );
  });

  it('for ≤5 actions, renders flat action blocks without grouping', () => {
    fc.assert(
      fc.property(
        fc.array(actionArb.filter((s) => s.trim().length > 0), { minLength: 1, maxLength: 5 }),
        (actions: string[]) => {
          const { container } = render(<ActionGroupCollapsible actions={actions} />);

          // Should NOT render the collapsible container
          const collapsible = container.querySelector('[data-testid="action-group-collapsible"]');
          expect(collapsible).toBeNull();

          // Should render flat action blocks
          const actionBlocks = container.querySelectorAll('[data-testid^="action-block-"]');
          expect(actionBlocks.length).toBe(actions.length);

          cleanup();
        },
      ),
      { numRuns: 50 },
    );
  });
});
