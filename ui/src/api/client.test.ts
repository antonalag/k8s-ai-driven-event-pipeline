import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fetchAnalyses, ApiError } from './client';
import type { ProblemDetail } from '../types/api';

describe('API Client Error Handling', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  describe('RFC 7807 ProblemDetail parsing', () => {
    it('parses a 400 response with application/problem+json content-type', async () => {
      const problem: ProblemDetail = {
        type: 'https://api.example.com/errors/validation',
        title: 'Validation Error',
        status: 400,
        detail: 'The namespace parameter is invalid',
        instance: '/api/v1/analyses?namespace=invalid',
      };

      const mockResponse = {
        ok: false,
        status: 400,
        headers: new Headers({ 'content-type': 'application/problem+json' }),
        json: vi.fn().mockResolvedValue(problem),
      };

      vi.mocked(fetch).mockResolvedValue(mockResponse as unknown as Response);

      await expect(fetchAnalyses()).rejects.toThrow(ApiError);

      try {
        await fetchAnalyses();
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError);
        const apiError = error as ApiError;
        expect(apiError.problem.type).toBe(problem.type);
        expect(apiError.problem.title).toBe(problem.title);
        expect(apiError.problem.status).toBe(400);
        expect(apiError.problem.detail).toBe(problem.detail);
        expect(apiError.problem.instance).toBe(problem.instance);
        expect(apiError.message).toBe('Validation Error');
      }
    });

    it('parses a 502 response with ProblemDetail payload', async () => {
      const problem: ProblemDetail = {
        title: 'Bad Gateway',
        status: 502,
        detail: 'AI provider is unavailable',
      };

      const mockResponse = {
        ok: false,
        status: 502,
        headers: new Headers({ 'content-type': 'application/problem+json; charset=utf-8' }),
        json: vi.fn().mockResolvedValue(problem),
      };

      vi.mocked(fetch).mockResolvedValue(mockResponse as unknown as Response);

      await expect(fetchAnalyses()).rejects.toThrow(ApiError);

      try {
        await fetchAnalyses();
      } catch (error) {
        const apiError = error as ApiError;
        expect(apiError.problem.title).toBe('Bad Gateway');
        expect(apiError.problem.status).toBe(502);
        expect(apiError.problem.detail).toBe('AI provider is unavailable');
        expect(apiError.problem.type).toBeUndefined();
        expect(apiError.problem.instance).toBeUndefined();
      }
    });

    it('parses a 503 response with ProblemDetail payload (circuit breaker open)', async () => {
      const problem: ProblemDetail = {
        title: 'Service Unavailable',
        status: 503,
        detail: 'Circuit breaker is open — retry later',
      };

      const mockResponse = {
        ok: false,
        status: 503,
        headers: new Headers({ 'content-type': 'application/problem+json' }),
        json: vi.fn().mockResolvedValue(problem),
      };

      vi.mocked(fetch).mockResolvedValue(mockResponse as unknown as Response);

      try {
        await fetchAnalyses();
      } catch (error) {
        const apiError = error as ApiError;
        expect(apiError).toBeInstanceOf(ApiError);
        expect(apiError.problem.status).toBe(503);
        expect(apiError.problem.title).toBe('Service Unavailable');
        expect(apiError.problem.detail).toBe('Circuit breaker is open — retry later');
      }
    });
  });

  describe('Network errors (fetch throws)', () => {
    it('propagates network errors when fetch rejects', async () => {
      vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(fetchAnalyses()).rejects.toThrow(TypeError);
      await expect(fetchAnalyses()).rejects.toThrow('Failed to fetch');
    });

    it('propagates DNS resolution failures', async () => {
      vi.mocked(fetch).mockRejectedValue(
        new TypeError('getaddrinfo ENOTFOUND api.example.com')
      );

      await expect(fetchAnalyses()).rejects.toThrow(TypeError);
    });
  });

  describe('Non-JSON error responses', () => {
    it('handles 502 Bad Gateway with HTML body (no problem+json content-type)', async () => {
      const mockResponse = {
        ok: false,
        status: 502,
        headers: new Headers({ 'content-type': 'text/html' }),
        json: vi.fn().mockRejectedValue(new SyntaxError('Unexpected token <')),
      };

      vi.mocked(fetch).mockResolvedValue(mockResponse as unknown as Response);

      await expect(fetchAnalyses()).rejects.toThrow(ApiError);

      try {
        await fetchAnalyses();
      } catch (error) {
        const apiError = error as ApiError;
        expect(apiError).toBeInstanceOf(ApiError);
        expect(apiError.problem.status).toBe(502);
        expect(apiError.problem.title).toBe('Unknown Error');
      }
    });

    it('handles responses with no content-type header', async () => {
      const mockResponse = {
        ok: false,
        status: 500,
        headers: new Headers(),
        json: vi.fn().mockRejectedValue(new SyntaxError('Unexpected end of JSON input')),
      };

      vi.mocked(fetch).mockResolvedValue(mockResponse as unknown as Response);

      await expect(fetchAnalyses()).rejects.toThrow(ApiError);

      try {
        await fetchAnalyses();
      } catch (error) {
        const apiError = error as ApiError;
        expect(apiError).toBeInstanceOf(ApiError);
        expect(apiError.problem.status).toBe(500);
        expect(apiError.problem.title).toBe('Unknown Error');
      }
    });
  });

  describe('Timeout scenarios', () => {
    it('propagates AbortError when request is aborted (timeout)', async () => {
      const abortError = new DOMException('The operation was aborted.', 'AbortError');
      vi.mocked(fetch).mockRejectedValue(abortError);

      await expect(fetchAnalyses()).rejects.toThrow('The operation was aborted.');
    });

    it('propagates timeout errors without wrapping in ApiError', async () => {
      const abortError = new DOMException('signal timed out', 'TimeoutError');
      vi.mocked(fetch).mockRejectedValue(abortError);

      await expect(fetchAnalyses()).rejects.toThrow(DOMException);

      try {
        await fetchAnalyses();
      } catch (error) {
        expect(error).not.toBeInstanceOf(ApiError);
        expect(error).toBeInstanceOf(DOMException);
      }
    });
  });

  describe('Error typing and propagation', () => {
    it('ApiError has correct name property', async () => {
      const problem: ProblemDetail = { title: 'Test Error', status: 400 };
      const mockResponse = {
        ok: false,
        status: 400,
        headers: new Headers({ 'content-type': 'application/problem+json' }),
        json: vi.fn().mockResolvedValue(problem),
      };

      vi.mocked(fetch).mockResolvedValue(mockResponse as unknown as Response);

      try {
        await fetchAnalyses();
      } catch (error) {
        const apiError = error as ApiError;
        expect(apiError.name).toBe('ApiError');
      }
    });

    it('ApiError extends Error', () => {
      const problem: ProblemDetail = { title: 'Some Error', status: 500 };
      const apiError = new ApiError(problem);
      expect(apiError).toBeInstanceOf(Error);
      expect(apiError).toBeInstanceOf(ApiError);
    });

    it('ApiError uses title as message, falls back to Unknown Error when title is undefined', () => {
      const withTitle = new ApiError({ title: 'Not Found', status: 404 });
      expect(withTitle.message).toBe('Not Found');

      const withoutTitle = new ApiError({ status: 500 });
      expect(withoutTitle.message).toBe('Unknown Error');
    });

    it('ApiError exposes the full ProblemDetail via problem property', () => {
      const problem: ProblemDetail = {
        type: 'urn:error:validation',
        title: 'Validation Failed',
        status: 422,
        detail: 'Field X is required',
        instance: '/api/v1/analyses',
      };
      const apiError = new ApiError(problem);
      expect(apiError.problem).toStrictEqual(problem);
    });
  });
});
