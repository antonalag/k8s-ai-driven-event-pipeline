import type { ProblemDetail } from '../types/api';

/**
 * Parses a raw JSON value into a ProblemDetail (RFC 7807).
 * Extracts only the known RFC 7807 fields, discarding unknown properties.
 * Returns undefined for absent or non-matching fields.
 */
export function parseProblemDetail(json: unknown): ProblemDetail {
  if (typeof json !== 'object' || json === null) {
    return {};
  }

  const raw = json as Record<string, unknown>;
  const result: ProblemDetail = {};

  if (typeof raw.type === 'string') {
    result.type = raw.type;
  }

  if (typeof raw.title === 'string') {
    result.title = raw.title;
  }

  if (typeof raw.status === 'number') {
    result.status = raw.status;
  }

  if (typeof raw.detail === 'string') {
    result.detail = raw.detail;
  }

  if (typeof raw.instance === 'string') {
    result.instance = raw.instance;
  }

  return result;
}
