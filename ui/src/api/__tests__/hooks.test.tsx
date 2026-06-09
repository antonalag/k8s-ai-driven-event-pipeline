import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useAnalyses } from '../hooks';
import { ApiError } from '../client';
import { createQueryWrapper } from '../../test-utils/queryWrapper';
import type { AiAnalysisResponse } from '../../types/api';

describe('useAnalyses hook', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it('returns data on successful fetch', async () => {
    const mockData: AiAnalysisResponse[] = [
      {
        podName: 'test-pod',
        namespace: 'default',
        verdict: 'HEALTHY',
        rootCauseAnalysis: 'All good',
        recommendedActions: [],
        analyzedAt: '2024-01-01T00:00:00Z',
      },
    ];
    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue(mockData),
    } as unknown as Response);

    const { result } = renderHook(() => useAnalyses(), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toStrictEqual(mockData);
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('returns loading state before fetch resolves', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));

    const { result } = renderHook(() => useAnalyses(), {
      wrapper: createQueryWrapper(),
    });

    expect(result.current.isLoading).toBe(true);
    expect(result.current.data).toBeUndefined();
  });

  it('returns ApiError with RFC 7807 ProblemDetail on error response', async () => {
    const problemBody = {
      title: 'Service Unavailable',
      status: 503,
      detail: 'Circuit breaker open',
    };

    vi.mocked(fetch).mockResolvedValue({
      ok: false,
      status: 503,
      headers: {
        get: (name: string) =>
          name.toLowerCase() === 'content-type'
            ? 'application/problem+json'
            : null,
      },
      json: vi.fn().mockResolvedValue(problemBody),
    } as unknown as Response);

    const { result } = renderHook(() => useAnalyses(), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(ApiError);
    expect(result.current.error!.problem.title).toBe('Service Unavailable');
  });

  it('returns TypeError on network failure', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));

    const { result } = renderHook(() => useAnalyses(), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(TypeError);
  });
});
