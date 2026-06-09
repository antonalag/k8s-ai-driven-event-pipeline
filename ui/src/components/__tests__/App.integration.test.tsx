import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as fc from 'fast-check';
import App from '../../App';
import type { ReactNode } from 'react';

/**
 * Property 5: Error State Renders ProblemDetail Fields
 * Validates: Requirements 5.1
 *
 * For any ProblemDetail with defined title and detail fields, when the error display
 * renders, the DOM text content SHALL include both the title value and the detail value
 * within an element with ARIA role "alert".
 */
describe('Feature: ui-backend-integration, Property 5: Error state renders ProblemDetail fields', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    cleanup();
  });

  const arbDefinedProblemDetail = fc.record({
    title: fc.string({ minLength: 1, maxLength: 100 }),
    status: fc.integer({ min: 400, max: 599 }),
    detail: fc.string({ minLength: 1, maxLength: 300 }),
  });

  it('DOM text content includes both title and detail within role="alert"', async () => {
    await fc.assert(
      fc.asyncProperty(arbDefinedProblemDetail, async (problem) => {
        // Fresh QueryClient per iteration to avoid cached results
        const queryClient = new QueryClient({
          defaultOptions: {
            queries: { retry: false },
          },
        });

        function Wrapper({ children }: { children: ReactNode }) {
          return (
            <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
          );
        }

        // Mock fetch to return the generated ProblemDetail as an error response
        vi.mocked(fetch).mockResolvedValue({
          ok: false,
          status: problem.status,
          headers: {
            get: (name: string) =>
              name.toLowerCase() === 'content-type'
                ? 'application/problem+json'
                : null,
          },
          json: vi.fn().mockResolvedValue(problem),
        } as unknown as Response);

        render(<App />, { wrapper: Wrapper });

        // Wait for error state to render
        const alertElement = await waitFor(() => screen.getByRole('alert'), {
          timeout: 3000,
        });

        // Assert: DOM text content includes both title and detail values
        const textContent = alertElement.textContent ?? '';
        expect(textContent).toContain(problem.title);
        expect(textContent).toContain(problem.detail);

        // Clean up between iterations
        cleanup();
      }),
      { numRuns: 20 },
    );
  });
});
