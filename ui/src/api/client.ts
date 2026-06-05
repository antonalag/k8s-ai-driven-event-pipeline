import type { AiAnalysisResponse, ProblemDetail } from '../types/api';
import { parseProblemDetail } from './parseProblemDetail';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

/**
 * Typed error wrapping an RFC 7807 ProblemDetail response.
 */
export class ApiError extends Error {
  public readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    super(problem.title ?? 'Unknown Error');
    this.name = 'ApiError';
    this.problem = problem;
  }
}

export interface QueryParams {
  namespace?: string;
  podName?: string;
}

function buildQuery(params?: QueryParams): string {
  if (!params) return '';
  const searchParams = new URLSearchParams();
  if (params.namespace) {
    searchParams.set('namespace', params.namespace);
  }
  if (params.podName) {
    searchParams.set('podName', params.podName);
  }
  return searchParams.toString();
}

/**
 * Fetches AI analyses from the backend service.
 * Parses RFC 7807 ProblemDetail on error responses (400, 502, 503).
 */
export async function fetchAnalyses(params?: QueryParams): Promise<AiAnalysisResponse[]> {
  const query = buildQuery(params);
  const url = query
    ? `${API_BASE_URL}/api/v1/analyses?${query}`
    : `${API_BASE_URL}/api/v1/analyses`;

  const response = await fetch(url);

  if (!response.ok) {
    const contentType = response.headers.get('content-type');
    if (contentType?.includes('application/problem+json')) {
      const problem: ProblemDetail = parseProblemDetail(await response.json());
      throw new ApiError(problem);
    }
    throw new ApiError({ status: response.status, title: 'Unknown Error' });
  }

  return response.json();
}
