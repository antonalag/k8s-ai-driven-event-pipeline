import { useQuery } from '@tanstack/react-query';
import type { AuditLogHistoryResponse } from '../types/api';
import { ApiError } from '../api/client';
import { parseProblemDetail } from '../api/parseProblemDetail';

const envBase = import.meta.env.VITE_API_BASE_URL;
const API_BASE_URL = (envBase && envBase !== 'undefined') ? envBase : '';

interface AuditLogParams {
  page: number;
  size: number;
}

/**
 * Fetches paginated audit log history from the backend.
 * Parses RFC 7807 ProblemDetail on error responses.
 */
async function fetchAuditLogHistory(params: AuditLogParams): Promise<AuditLogHistoryResponse> {
  const url = `${API_BASE_URL}/api/v1/analyses/history?page=${params.page}&size=${params.size}`;
  const response = await fetch(url);

  if (!response.ok) {
    const contentType = response.headers.get('content-type');
    if (contentType?.includes('application/problem+json') || contentType?.includes('application/json')) {
      const problem = parseProblemDetail(await response.json());
      throw new ApiError(problem);
    }
    throw new ApiError({ status: response.status, title: 'Unknown Error' });
  }

  return response.json();
}

/**
 * TanStack Query hook for fetching paginated audit log history.
 * Uses a 30-second stale time (no polling) — data is cached for 30s before refetching.
 */
export function useAuditLogHistory(params: AuditLogParams) {
  return useQuery<AuditLogHistoryResponse, ApiError>({
    queryKey: ['audit-log-history', params.page, params.size],
    queryFn: () => fetchAuditLogHistory(params),
    staleTime: 30_000,
  });
}
