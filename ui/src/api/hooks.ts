import { useQuery } from '@tanstack/react-query';
import type { AiAnalysisResponse } from '../types/api';
import { fetchAnalyses, ApiError } from './client';

interface AnalysesQueryParams {
  namespace?: string;
  podName?: string;
}

/**
 * TanStack Query hook for fetching AI analyses.
 * Refetches automatically when params change.
 */
export function useAnalyses(params?: AnalysesQueryParams) {
  return useQuery<AiAnalysisResponse[], ApiError>({
    queryKey: ['analyses', params],
    queryFn: () => fetchAnalyses(params),
  });
}
