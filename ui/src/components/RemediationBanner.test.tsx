/**
 * Tests for RemediationBanner component.
 * Validates: Requirement 11.4, 11.5 (success/error banner rendering)
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, cleanup, fireEvent } from '@testing-library/react';
import { RemediationBanner } from './RemediationBanner';
import type { RemediationResponse, RemediationError } from '../types/remediation';

afterEach(() => {
  cleanup();
});

describe('RemediationBanner', () => {
  describe('success variant', () => {
    const mockResponse: RemediationResponse = {
      correlationId: '550e8400-e29b-41d4-a716-446655440000',
      action: 'restart_deployment',
      status: 'completed',
      timestamp: '2026-06-18T10:30:00.000Z',
      details: { annotation: 'kubectl.kubernetes.io/restartedAt' },
    };

    it('renders success banner with action name', () => {
      const { container } = render(<RemediationBanner variant="success" response={mockResponse} />);

      const banner = container.querySelector('[data-testid="remediation-banner-success"]');
      expect(banner).not.toBeNull();
      expect(banner!.textContent).toContain('restart_deployment');
    });

    it('renders timestamp in locale format', () => {
      const { container } = render(<RemediationBanner variant="success" response={mockResponse} />);

      const banner = container.querySelector('[data-testid="remediation-banner-success"]');
      expect(banner).not.toBeNull();
      expect(banner!.textContent).toContain('completed at');
    });

    it('has role="alert" for accessibility', () => {
      const { container } = render(<RemediationBanner variant="success" response={mockResponse} />);

      const banner = container.querySelector('[role="alert"]');
      expect(banner).not.toBeNull();
      expect(banner!.getAttribute('aria-label')).toBe('Remediation success');
    });
  });

  describe('error variant', () => {
    const mockError: RemediationError = {
      title: 'Mutation Circuit Breaker Open',
      status: 503,
      detail: 'Mutation circuit breaker is open — cluster write operations temporarily suspended',
      errorCode: 'CIRCUIT_OPEN',
    };

    const mockRetry = vi.fn();

    it('renders error banner with title and detail', () => {
      const { container } = render(
        <RemediationBanner variant="error" error={mockError} onRetry={mockRetry} />,
      );

      const title = container.querySelector('[data-testid="error-title"]');
      const detail = container.querySelector('[data-testid="error-detail"]');
      expect(title).not.toBeNull();
      expect(title!.textContent).toBe('Mutation Circuit Breaker Open');
      expect(detail).not.toBeNull();
      expect(detail!.textContent).toContain('circuit breaker is open');
    });

    it('renders retry button that calls onRetry', () => {
      const { container } = render(
        <RemediationBanner variant="error" error={mockError} onRetry={mockRetry} />,
      );

      const retryButton = container.querySelector('[data-testid="retry-button"]') as HTMLButtonElement;
      expect(retryButton).not.toBeNull();

      fireEvent.click(retryButton);
      expect(mockRetry).toHaveBeenCalledOnce();
    });

    it('has role="alert" for accessibility', () => {
      const { container } = render(
        <RemediationBanner variant="error" error={mockError} onRetry={mockRetry} />,
      );

      const banner = container.querySelector('[role="alert"]');
      expect(banner).not.toBeNull();
      expect(banner!.getAttribute('aria-label')).toBe('Remediation error');
    });
  });
});
