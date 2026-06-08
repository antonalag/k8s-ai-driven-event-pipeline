// Feature: ui-stitch-visual-integration, Property 4: ProblemDetail field rendering

/**
 * **Validates: Requirements 6.3**
 *
 * For any valid ProblemDetailDisplay object (with non-empty type, issue, status,
 * and description strings), the rendered AI Diagnosis panel SHALL display all
 * four field values in the output.
 */

// @vitest-environment jsdom
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { render, cleanup } from '@testing-library/react';
import AIDiagnosisPanel from '../components/AIDiagnosisPanel';
import type { ProblemDetailDisplay } from '../types/dashboard';

const problemDetailArb = fc.record({
  type: fc.string({ minLength: 1, maxLength: 200 }),
  issue: fc.string({ minLength: 1, maxLength: 100 }),
  status: fc.string({ minLength: 1, maxLength: 50 }),
  description: fc.string({ minLength: 1, maxLength: 500 }),
});

describe('Property 4: ProblemDetail field rendering', () => {
  it('for any valid ProblemDetailDisplay, all four field values appear in rendered output', () => {
    fc.assert(
      fc.property(problemDetailArb, (problemDetail: ProblemDetailDisplay) => {
        const { getByTestId } = render(
          <AIDiagnosisPanel
            problemDetail={problemDetail}
            correlatedEvents={[]}
            remediationCommands={['kubectl get pods']}
            confidence={85}
          />,
        );

        // All four ProblemDetail fields must appear in their respective elements
        expect(getByTestId('problem-type').textContent).toContain(problemDetail.type);
        expect(getByTestId('problem-issue').textContent).toContain(problemDetail.issue);
        expect(getByTestId('problem-status').textContent).toContain(problemDetail.status);
        expect(getByTestId('problem-description').textContent).toContain(problemDetail.description);

        // Cleanup to avoid DOM leaks across iterations
        cleanup();
      }),
      { numRuns: 100 },
    );
  });
});
