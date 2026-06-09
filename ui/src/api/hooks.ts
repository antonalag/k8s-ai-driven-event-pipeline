import { useQuery } from '@tanstack/react-query';
import type { AiAnalysisResponse } from '../types/api';
import { fetchAnalyses, ApiError } from './client';

interface AnalysesQueryParams {
  namespace?: string;
  podName?: string;
}

/**
 * TanStack Query hook for fetching AI analyses with real-time polling.
 * Polls every 5 seconds while the browser tab is visible.
 * Pauses polling when the tab is hidden.
 */
export function useAnalyses(params?: AnalysesQueryParams) {
  return useQuery<AiAnalysisResponse[], ApiError>({
    queryKey: ['analyses', params],
    queryFn: () => fetchAnalyses(params),
    refetchInterval: 5000,
    refetchIntervalInBackground: false,
  });
}
