// Unit tests for AuditLogView component
// Validates: Requirements 3.1, 3.2, 3.7, 3.8, 3.10

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { HistoryResponseDto, AuditLogHistoryResponse } from '../types/api';

vi.mock('../hooks/useAuditLogHistory', () => ({
  useAuditLogHistory: vi.fn(),
}));

import { useAuditLogHistory } from '../hooks/useAuditLogHistory';
import { AuditLogView } from '../components/AuditLogView';
import { ApiError } from '../api/client';
import Sidebar from '../components/Sidebar';

const mockUseAuditLogHistory = vi.mocked(useAuditLogHistory);

beforeEach(() => {
  cleanup();
  mockUseAuditLogHistory.mockReset();
});

function createMockEntry(overrides?: Partial<HistoryResponseDto>): HistoryResponseDto {
  return {
    resolvedAt: '2025-07-06T12:30:00Z',
    podName: 'test-pod-abc123',
    namespace: 'default',
    verdict: 'CRITICAL_FAILURE',
    status: 'REMEDIATED',
    rootCauseAnalysis: 'CrashLoopBackOff',
    recommendedActions: ['kubectl rollout restart'],
    modelUsed: 'llama3.1:8b',
    ...overrides,
  };
}

describe('AuditLogView - Loading state', () => {
  it('renders skeleton rows when loading', () => {
    mockUseAuditLogHistory.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    } as ReturnType<typeof useAuditLogHistory>);

    const { container } = render(<AuditLogView />);

    // Skeleton rows use animate-pulse class
    const pulseElements = container.querySelectorAll('[class*="kd-animate-pulse"]');
    expect(pulseElements.length).toBeGreaterThan(0);
  });
});

describe('AuditLogView - Empty state', () => {
  it('renders empty state message when no data', () => {
    mockUseAuditLogHistory.mockReturnValue({
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useAuditLogHistory>);

    render(<AuditLogView />);

    expect(screen.getByText('No resolved analyses yet')).toBeDefined();
    expect(screen.getByRole('status')).toBeDefined();
  });
});

describe('AuditLogView - Error state', () => {
  it('renders dismissible notification with title and detail', () => {
    const apiError = new ApiError({
      title: 'Validation Error',
      detail: 'page must be >= 0',
      status: 400,
    });

    mockUseAuditLogHistory.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: apiError,
    } as ReturnType<typeof useAuditLogHistory>);

    render(<AuditLogView />);

    const alert = screen.getByRole('alert');
    expect(alert).toBeDefined();
    expect(alert.textContent).toContain('Validation Error');
    expect(alert.textContent).toContain('page must be >= 0');

    // Dismiss button exists
    const dismissButton = screen.getByLabelText('Dismiss error');
    expect(dismissButton).toBeDefined();
  });
});

describe('AuditLogView - Table rendering with data', () => {
  it('renders correct columns and row data', () => {
    const entries: HistoryResponseDto[] = [
      createMockEntry(),
      createMockEntry({
        podName: 'worker-pod-xyz789',
        verdict: 'DEGRADED',
        status: 'DISMISSED',
        modelUsed: 'gpt-4o',
      }),
    ];

    mockUseAuditLogHistory.mockReturnValue({
      data: {
        content: entries,
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      },
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useAuditLogHistory>);

    const { container } = render(<AuditLogView />);

    // Assert table headers
    const headers = container.querySelectorAll('th');
    const headerTexts = Array.from(headers).map((h) => h.textContent?.trim());
    expect(headerTexts).toContain('Resolved At');
    expect(headerTexts).toContain('Pod Name');
    expect(headerTexts).toContain('Verdict');
    expect(headerTexts).toContain('Action');
    expect(headerTexts).toContain('LLM Model');

    // Assert row data is rendered
    expect(container.textContent).toContain('test-pod-abc123');
    expect(container.textContent).toContain('CRITICAL_FAILURE');
    expect(container.textContent).toContain('llama3.1:8b');

    expect(container.textContent).toContain('worker-pod-xyz789');
    expect(container.textContent).toContain('DEGRADED');
    expect(container.textContent).toContain('gpt-4o');
  });
});

describe('Sidebar - Audit Log tab', () => {
  it('shows "Audit Log" navigation item', () => {
    const { container } = render(
      <Sidebar activeNavItem="audit-log" onNavItemClick={() => {}} />,
    );

    const navButtons = container.querySelectorAll('nav button');
    const auditLogButton = Array.from(navButtons).find((btn) =>
      btn.textContent?.includes('Audit Log'),
    );
    expect(auditLogButton).toBeDefined();
  });
});
