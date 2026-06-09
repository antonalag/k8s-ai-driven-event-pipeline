import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { mapAnalysisToProps, mapToCorrelatedEvents, mapProblemDetailToDisplay, VERDICT_DISPLAY_MAP, VERDICT_SEVERITY_MAP } from '../mappers';
import type { AiAnalysisResponse } from '../../types/api';
import type { ProblemDetail } from '../../types/api';

/**
 * Arbitrary generator for AiAnalysisResponse — exported for reuse in other test files (e.g., task 2.3).
 */
export const arbAiAnalysisResponse: fc.Arbitrary<AiAnalysisResponse> = fc.record({
  podName: fc.string({ minLength: 1, maxLength: 63 }),
  namespace: fc.string({ minLength: 1, maxLength: 63 }),
  verdict: fc.constantFrom('HEALTHY' as const, 'TRANSIENT_ISSUE' as const, 'CRITICAL_FAILURE' as const),
  rootCauseAnalysis: fc.string({ minLength: 1, maxLength: 500 }),
  recommendedActions: fc.array(fc.string({ minLength: 1 }), { maxLength: 10 }),
  analyzedAt: fc.integer({ min: 0, max: 1893456000000 }).map(ts => new Date(ts).toISOString()),
});

/**
 * Property 1: DTO Mapping Structural Completeness
 * Validates: Requirements 3.3, 8.1, 8.2, 8.3, 8.5, 8.7
 */
describe('Feature: ui-backend-integration, Property 1: DTO mapping structural completeness', () => {
  it('problemDetail.status equals VERDICT_DISPLAY_MAP[verdict]', () => {
    fc.assert(
      fc.property(arbAiAnalysisResponse, (response) => {
        const result = mapAnalysisToProps(response);
        expect(result.problemDetail.status).toBe(VERDICT_DISPLAY_MAP[response.verdict]);
      }),
      { numRuns: 100 },
    );
  });

  it('problemDetail.description equals rootCauseAnalysis', () => {
    fc.assert(
      fc.property(arbAiAnalysisResponse, (response) => {
        const result = mapAnalysisToProps(response);
        expect(result.problemDetail.description).toBe(response.rootCauseAnalysis);
      }),
      { numRuns: 100 },
    );
  });

  it('problemDetail.issue is a non-empty string of at most 120 characters', () => {
    fc.assert(
      fc.property(arbAiAnalysisResponse, (response) => {
        const result = mapAnalysisToProps(response);
        expect(result.problemDetail.issue.length).toBeGreaterThan(0);
        expect(result.problemDetail.issue.length).toBeLessThanOrEqual(120);
      }),
      { numRuns: 100 },
    );
  });

  it('remediationCommands strictly equals recommendedActions', () => {
    fc.assert(
      fc.property(arbAiAnalysisResponse, (response) => {
        const result = mapAnalysisToProps(response);
        expect(result.remediationCommands).toStrictEqual(response.recommendedActions);
      }),
      { numRuns: 100 },
    );
  });

  it('confidence equals 1.0', () => {
    fc.assert(
      fc.property(arbAiAnalysisResponse, (response) => {
        const result = mapAnalysisToProps(response);
        expect(result.confidence).toBe(1.0);
      }),
      { numRuns: 100 },
    );
  });

  it('referential transparency: same input yields identical output', () => {
    fc.assert(
      fc.property(arbAiAnalysisResponse, (response) => {
        const result1 = mapAnalysisToProps(response);
        const result2 = mapAnalysisToProps(response);
        expect(result1).toStrictEqual(result2);
      }),
      { numRuns: 100 },
    );
  });
});

/**
 * Property 4: ProblemDetail Display Mapping with Fallbacks
 * Validates: Requirements 7.5, 7.6
 *
 * For any ProblemDetail object with an arbitrary subset of fields defined or undefined,
 * mapProblemDetailToDisplay SHALL produce a ProblemDetailDisplay where every field is
 * a non-empty string — either the mapped value when defined, or the designated fallback
 * placeholder when undefined.
 */
describe('Feature: ui-backend-integration, Property 4: ProblemDetail display mapping with fallbacks', () => {
  const arbProblemDetail = fc.record({
    type: fc.option(fc.webUrl(), { nil: undefined }),
    title: fc.option(fc.string({ minLength: 1 }), { nil: undefined }),
    status: fc.option(fc.integer({ min: 100, max: 599 }), { nil: undefined }),
    detail: fc.option(fc.string({ minLength: 1 }), { nil: undefined }),
    instance: fc.option(fc.string({ minLength: 1 }), { nil: undefined }),
  });

  it('every output field is a non-empty string (mapped value or fallback)', () => {
    fc.assert(
      fc.property(arbProblemDetail, (problem: ProblemDetail) => {
        const result = mapProblemDetailToDisplay(problem);

        // Every output field must be a non-empty string
        expect(result.type.length).toBeGreaterThan(0);
        expect(result.issue.length).toBeGreaterThan(0);
        expect(result.status.length).toBeGreaterThan(0);
        expect(result.description.length).toBeGreaterThan(0);
      }),
      { numRuns: 100 },
    );
  });

  it('uses correct fallback values when fields are undefined', () => {
    fc.assert(
      fc.property(arbProblemDetail, (problem: ProblemDetail) => {
        const result = mapProblemDetailToDisplay(problem);

        if (problem.type === undefined) {
          expect(result.type).toBe('—');
        }
        if (problem.title === undefined) {
          expect(result.issue).toBe('API Error');
        }
        if (problem.status === undefined) {
          expect(result.status).toBe('—');
        }
        if (problem.detail === undefined) {
          expect(result.description).toBe(
            'An unexpected error occurred. Please retry or contact your platform team.',
          );
        }
      }),
      { numRuns: 100 },
    );
  });

  it('maps defined fields correctly without fallbacks', () => {
    fc.assert(
      fc.property(arbProblemDetail, (problem: ProblemDetail) => {
        const result = mapProblemDetailToDisplay(problem);

        if (problem.type !== undefined) {
          expect(result.type).toBe(problem.type);
        }
        if (problem.title !== undefined) {
          expect(result.issue).toBe(problem.title);
        }
        if (problem.status !== undefined) {
          expect(result.status).toBe(String(problem.status));
        }
        if (problem.detail !== undefined) {
          expect(result.description).toBe(problem.detail);
        }
      }),
      { numRuns: 100 },
    );
  });
});


/**
 * Property 2: Correlated Event Derivation
 * Validates: Requirements 3.4
 *
 * For any array of valid AiAnalysisResponse records, mapToCorrelatedEvents SHALL produce
 * a CorrelatedEvent[] of the same length, where each entry's severity matches the
 * deterministic verdict mapping and description equals the corresponding rootCauseAnalysis.
 */
describe('Feature: ui-backend-integration, Property 2: Correlated event derivation', () => {
  it('output array length equals input array length', () => {
    fc.assert(
      fc.property(fc.array(arbAiAnalysisResponse, { maxLength: 20 }), (responses) => {
        const result = mapToCorrelatedEvents(responses);
        expect(result).toHaveLength(responses.length);
      }),
      { numRuns: 100 },
    );
  });

  it('each entry severity matches VERDICT_SEVERITY_MAP[input verdict]', () => {
    fc.assert(
      fc.property(fc.array(arbAiAnalysisResponse, { maxLength: 20 }), (responses) => {
        const result = mapToCorrelatedEvents(responses);
        for (let i = 0; i < responses.length; i++) {
          expect(result[i].severity).toBe(VERDICT_SEVERITY_MAP[responses[i].verdict]);
        }
      }),
      { numRuns: 100 },
    );
  });

  it('each entry description equals the corresponding rootCauseAnalysis', () => {
    fc.assert(
      fc.property(fc.array(arbAiAnalysisResponse, { maxLength: 20 }), (responses) => {
        const result = mapToCorrelatedEvents(responses);
        for (let i = 0; i < responses.length; i++) {
          expect(result[i].description).toBe(responses[i].rootCauseAnalysis);
        }
      }),
      { numRuns: 100 },
    );
  });
});
