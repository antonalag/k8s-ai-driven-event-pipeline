import { useMutation } from '@tanstack/react-query';
import type { RemediationRequest, RemediationResponse, RemediationError } from '../types/remediation';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

/**
 * Executes a remediation request against the backend.
 * Parses RFC 7807 ProblemDetail on error responses.
 */
async function executeRemediation(request: RemediationRequest): Promise<RemediationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/remediations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const contentType = response.headers.get('content-type');
    if (contentType?.includes('application/problem+json') || contentType?.includes('application/json')) {
      const problem = await response.json() as RemediationError;
      throw problem;
    }
    throw {
      title: 'Remediation Failed',
      status: response.status,
      detail: `HTTP ${response.status}: ${response.statusText}`,
    } satisfies RemediationError;
  }

  return response.json();
}

/**
 * TanStack Mutation hook for executing a remediation action.
 * Exposes: mutate, isPending, isSuccess, isError, data, error, reset.
 */
export function useRemediation() {
  return useMutation<RemediationResponse, RemediationError, RemediationRequest>({
    mutationFn: executeRemediation,
  });
}
