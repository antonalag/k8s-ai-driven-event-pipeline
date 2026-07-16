import { useMutation, useQueryClient } from '@tanstack/react-query';

const envBase = import.meta.env.VITE_API_BASE_URL;
const API_BASE_URL = (envBase && envBase !== 'undefined') ? envBase : '';

export interface DismissRequest {
  analysisId: string;
  reason?: string;
}

export interface DismissResponse {
  id: string;
  status: string;
}

export interface DismissError {
  type?: string;
  title: string;
  status: number;
  detail: string;
  analysisId?: string;
  currentStatus?: string;
}

async function dismissAnalysis({ analysisId, reason }: DismissRequest): Promise<DismissResponse> {
  const body = reason ? JSON.stringify({ reason }) : undefined;

  const response = await fetch(`${API_BASE_URL}/api/v1/analyses/${analysisId}/dismiss`, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body,
  });

  if (!response.ok) {
    const contentType = response.headers.get('content-type');
    if (contentType?.includes('application/problem+json') || contentType?.includes('application/json')) {
      const problem = await response.json() as DismissError;
      throw problem;
    }
    throw {
      title: 'Dismiss Failed',
      status: response.status,
      detail: `HTTP ${response.status}: ${response.statusText}`,
    } satisfies DismissError;
  }

  return response.json();
}

export function useDismissAnalysis() {
  const queryClient = useQueryClient();

  return useMutation<DismissResponse, DismissError, DismissRequest>({
    mutationFn: dismissAnalysis,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['analyses'] });
    },
  });
}
