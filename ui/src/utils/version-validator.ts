/**
 * Validates whether a dependency version string is properly pinned (exact version).
 *
 * A pinned version is an exact semver string like "1.2.3" with no range operators.
 * Range operators include: ^, ~, >=, <=, >, <, ||, " - " (hyphen range), *, x
 *
 * @param version - The version string from a package.json dependency entry
 * @returns true if the version is pinned (exact), false if it contains range operators
 */
export function isPinnedVersion(version: string): boolean {
  if (version.length === 0) {
    return false;
  }

  // Range operator prefixes
  if (version.startsWith('^') || version.startsWith('~')) {
    return false;
  }

  // Comparison operators
  if (
    version.startsWith('>=') ||
    version.startsWith('<=') ||
    version.startsWith('>') ||
    version.startsWith('<')
  ) {
    return false;
  }

  // OR ranges
  if (version.includes('||')) {
    return false;
  }

  // Hyphen ranges (space-dash-space)
  if (version.includes(' - ')) {
    return false;
  }

  // Wildcard characters
  if (version.includes('*')) {
    return false;
  }

  // x-range wildcard: match standalone 'x' or 'X' used as version segment
  // e.g., "1.x", "1.2.x", "x" but not "1.2.3-next" where x is in prerelease
  if (/(?:^|\.)(?:x|X)(?:\.|$)/.test(version)) {
    return false;
  }

  return true;
}
