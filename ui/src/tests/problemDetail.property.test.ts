import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import type { ProblemDetail } from '../types/api';
import { parseProblemDetail } from '../api/parseProblemDetail';

/**
 * Feature: ui-observability-dashboard
 * Property 1: ProblemDetail RFC 7807 parsing round-trip
 *
 * **Validates: Requirements 9.5**
 *
 * For any valid ProblemDetail object with arbitrary combinations of optional
 * fields (type, title, status, detail, instance), serializing it to JSON and
 * parsing it through the SPA's error parser SHALL produce a ProblemDetail
 * instance where every present field matches the original value exactly, and
 * absent fields remain undefined.
 */
describe('ProblemDetail RFC 7807 parsing round-trip', () => {
  const problemDetailArbitrary: fc.Arbitrary<ProblemDetail> = fc.record(
    {
      type: fc.webUrl(),
      title: fc.string({ minLength: 1, maxLength: 200 }),
      status: fc.integer({ min: 100, max: 599 }),
      detail: fc.string({ minLength: 0, maxLength: 500 }),
      instance: fc.webUrl(),
    },
    { requiredKeys: [] },
  );

  it('round-trip: serialize to JSON and parse back produces identical ProblemDetail', () => {
    fc.assert(
      fc.property(problemDetailArbitrary, (original: ProblemDetail) => {
        // Serialize to JSON (simulating network transport)
        const json = JSON.stringify(original);

        // Parse back through the project's parser
        const parsed = parseProblemDetail(JSON.parse(json));

        // Assert field-by-field equality
        expect(parsed.type).toBe(original.type);
        expect(parsed.title).toBe(original.title);
        expect(parsed.status).toBe(original.status);
        expect(parsed.detail).toBe(original.detail);
        expect(parsed.instance).toBe(original.instance);
      }),
      { numRuns: 100 },
    );
  });

  it('absent fields remain undefined after round-trip', () => {
    fc.assert(
      fc.property(problemDetailArbitrary, (original: ProblemDetail) => {
        const json = JSON.stringify(original);
        const parsed = parseProblemDetail(JSON.parse(json));

        // Check that fields absent in original remain undefined in parsed
        if (original.type === undefined) expect(parsed.type).toBeUndefined();
        if (original.title === undefined) expect(parsed.title).toBeUndefined();
        if (original.status === undefined) expect(parsed.status).toBeUndefined();
        if (original.detail === undefined) expect(parsed.detail).toBeUndefined();
        if (original.instance === undefined) expect(parsed.instance).toBeUndefined();
      }),
      { numRuns: 100 },
    );
  });
});
