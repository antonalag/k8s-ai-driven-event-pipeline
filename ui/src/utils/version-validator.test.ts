import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { isPinnedVersion } from './version-validator';

/**
 * Property 2: Version pinning validator correctness
 *
 * For any version string in a package.json dependency entry, the version pinning
 * validator SHALL classify it as "pinned" if and only if it contains no range
 * operators (^, ~, >=, <=, >, <, ||, " - ", *, x), and as "ranged" otherwise.
 *
 * **Validates: Requirements 10.3**
 */
describe('Feature: ui-observability-dashboard, Property 2: Version pinning validator correctness', () => {
  /**
   * Arbitrary: generates valid pinned semver version strings (exact versions).
   * Format: MAJOR.MINOR.PATCH with optional prerelease and build metadata.
   */
  const pinnedVersionArb = fc
    .tuple(
      fc.nat({ max: 999 }),
      fc.nat({ max: 999 }),
      fc.nat({ max: 999 }),
    )
    .map(([major, minor, patch]) => `${major}.${minor}.${patch}`);

  /**
   * Arbitrary: generates pinned versions with optional prerelease identifiers.
   * Prerelease identifiers use only alphanumeric chars and hyphens (no 'x' alone as segment).
   */
  const prereleaseSegmentArb = fc
    .array(fc.constantFrom('alpha', 'beta', 'rc', '0', '1', '2', '3'), {
      minLength: 1,
      maxLength: 3,
    })
    .map((parts) => parts.join('.'));

  const pinnedVersionWithPrereleaseArb = fc
    .tuple(pinnedVersionArb, fc.boolean(), prereleaseSegmentArb)
    .map(([version, hasPrerelease, prerelease]) =>
      hasPrerelease ? `${version}-${prerelease}` : version,
    );

  /** Range operator prefixes to prepend to a version */
  const rangePrefixArb = fc.constantFrom('^', '~', '>=', '<=', '>', '<');

  /** Generates version strings that use range prefixes */
  const prefixedRangeVersionArb = fc
    .tuple(rangePrefixArb, pinnedVersionArb)
    .map(([prefix, version]) => `${prefix}${version}`);

  /** Generates version strings with OR (||) operator */
  const orRangeVersionArb = fc
    .tuple(pinnedVersionArb, pinnedVersionArb)
    .map(([v1, v2]) => `${v1} || ${v2}`);

  /** Generates version strings with hyphen range (" - ") operator */
  const hyphenRangeVersionArb = fc
    .tuple(pinnedVersionArb, pinnedVersionArb)
    .map(([v1, v2]) => `${v1} - ${v2}`);

  /** Generates version strings with wildcard * */
  const starWildcardVersionArb = fc.constantFrom('*', '1.*', '1.2.*');

  /** Generates version strings with x-range wildcard */
  const xRangeVersionArb = fc.constantFrom('x', '1.x', '1.2.x', 'X', '1.X', '1.2.X');

  /** Combined arbitrary for all range version patterns */
  const rangeVersionArb = fc.oneof(
    prefixedRangeVersionArb,
    orRangeVersionArb,
    hyphenRangeVersionArb,
    starWildcardVersionArb,
    xRangeVersionArb,
  );

  it('should classify all pinned versions as pinned (true)', () => {
    fc.assert(
      fc.property(pinnedVersionWithPrereleaseArb, (version) => {
        expect(isPinnedVersion(version)).toBe(true);
      }),
      { numRuns: 200 },
    );
  });

  it('should classify all range versions as not pinned (false)', () => {
    fc.assert(
      fc.property(rangeVersionArb, (version) => {
        expect(isPinnedVersion(version)).toBe(false);
      }),
      { numRuns: 200 },
    );
  });

  it('should distinguish pinned from ranged: no range operator implies pinned', () => {
    fc.assert(
      fc.property(pinnedVersionArb, (version) => {
        // A version without any range operators should be pinned
        const hasRangeOp =
          version.startsWith('^') ||
          version.startsWith('~') ||
          version.startsWith('>=') ||
          version.startsWith('<=') ||
          version.startsWith('>') ||
          version.startsWith('<') ||
          version.includes('||') ||
          version.includes(' - ') ||
          version.includes('*') ||
          /(?:^|\.)(?:x|X)(?:\.|$)/.test(version);

        expect(hasRangeOp).toBe(false);
        expect(isPinnedVersion(version)).toBe(true);
      }),
      { numRuns: 200 },
    );
  });

  it('should reject empty strings', () => {
    expect(isPinnedVersion('')).toBe(false);
  });
});
