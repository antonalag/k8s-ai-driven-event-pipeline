// Feature: audit-log-history, Properties 8–11: Frontend property-based tests
// @vitest-environment jsdom

import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { render, cleanup } from '@testing-library/react';

vi.mock('../hooks/useAuditLogHistory', () => ({
  useAuditLogHistory: vi.fn(),
}));

import { useAuditLogHistory } from '../hooks/useAuditLogHistory';
import { AuditLogView } from '../components/AuditLogView';
import { ApiError } from '../api/client';
import type { HistoryResponseDto, ResolvedStatus, AuditLogHistoryResponse } from '../types/api';

const mockUseAuditLogHistory = vi.mocked(useAuditLogHistory);

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// --- Arbitraries ---

const resolvedStatusArb: fc.Arbitrary<ResolvedStatus> = fc.constantFrom('REMEDIATED', 'DISMISSED');

const historyEntryArb: fc.Arbitrary<HistoryResponseDto> = fc.record({
  resolvedAt: fc.integer({ min: 1577836800000, max: 1893456000000 }).map(ts => new Date(ts).toISOString()),
  podName: fc.string({ minLength: 1, maxLength: 50 }).filter(s => s.trim().length > 0),
  namespace: fc.string({ minLength: 1, maxLength: 30 }).filter(s => s.trim().length > 0),
  verdict: fc.string({ minLength: 1, maxLength: 50 }).filter(s => s.trim().length > 0),
  status: resolvedStatusArb,
  rootCauseAnalysis: fc.string({ minLength: 1, maxLength: 200 }),
  recommendedActions: fc.array(fc.string({ minLength: 1, maxLength: 50 }), { minLength: 0, maxLength: 5 }),
  modelUsed: fc.string({ minLength: 1, maxLength: 50 }).filter(s => s.trim().length > 0),
});

// --- Property 8: Status-Driven Badge Color Mapping ---

/**
 * **Validates: Requirements 3.3, 3.4**
 *
 * For any entry with status REMEDIATED, the rendered badge SHALL contain `kd-text-primary` (green).
 * For any entry with status DISMISSED, the rendered badge SHALL contain `kd-text-tertiary` (amber).
 */
describe('Property 8: Status-Driven Badge Color Mapping', () => {
  it('for any entry, REMEDIATED renders green badge and DISMISSED renders amber badge', () => {
    fc.assert(
      fc.property(historyEntryArb, (entry: HistoryResponseDto) => {
        cleanup();

        const mockData: AuditLogHistoryResponse = {
          content: [entry],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        };

        mockUseAuditLogHistory.mockReturnValue({
          data: mockData,
          isLoading: false,
          isError: false,
          error: null,
        } as ReturnType<typeof useAuditLogHistory>);

        const { container } = render(<AuditLogView />);

        // Find the status badge span
        const badges = container.querySelectorAll('span.kd-rounded.kd-text-label-caps');
        expect(badges.length).toBeGreaterThanOrEqual(1);

        const badge = badges[0];
        if (entry.status === 'REMEDIATED') {
          expect(badge.className).toContain('kd-text-primary');
          expect(badge.className).toContain('kd-bg-primary/10');
        } else {
          expect(badge.className).toContain('kd-text-tertiary');
          expect(badge.className).toContain('kd-bg-tertiary/10');
        }
      }),
      { numRuns: 100 },
    );
  });
});

// --- Property 9: Pagination Control State Correctness ---

/**
 * **Validates: Requirements 3.6, 3.11**
 *
 * When page is 0, "Previous" button SHALL be disabled.
 * When content.length < PAGE_SIZE (20), "Next" button SHALL be disabled.
 */
describe('Property 9: Pagination Control State Correctness', () => {
  it('Previous is disabled at page 0; Next is disabled when content < PAGE_SIZE', () => {
    const PAGE_SIZE = 20;

    const paginationArb = fc.record({
      page: fc.nat({ max: 10 }),
      contentSize: fc.nat({ max: 25 }),
      totalPages: fc.integer({ min: 1, max: 20 }),
    });

    fc.assert(
      fc.property(paginationArb, ({ page, contentSize, totalPages }) => {
        cleanup();

        // Generate content entries of the specified size
        const content: HistoryResponseDto[] = Array.from({ length: contentSize }, (_, i) => ({
          resolvedAt: new Date(2025, 0, 1, 0, 0, i).toISOString(),
          podName: `pod-${i}`,
          namespace: 'default',
          verdict: 'CRITICAL_FAILURE',
          status: 'REMEDIATED' as ResolvedStatus,
          rootCauseAnalysis: 'Some cause',
          recommendedActions: [],
          modelUsed: 'llama3.1:8b',
        }));

        const mockData: AuditLogHistoryResponse = {
          content,
          page,
          size: PAGE_SIZE,
          totalElements: totalPages * PAGE_SIZE,
          totalPages,
        };

        mockUseAuditLogHistory.mockReturnValue({
          data: mockData,
          isLoading: false,
          isError: false,
          error: null,
        } as ReturnType<typeof useAuditLogHistory>);

        const { container } = render(<AuditLogView />);

        // Only check pagination controls if content is non-empty (pagination renders only when data present)
        if (contentSize === 0) return;

        const buttons = container.querySelectorAll('button[type="button"]');
        const prevBtn = Array.from(buttons).find(b => b.textContent?.includes('Previous'));
        const nextBtn = Array.from(buttons).find(b => b.textContent?.includes('Next'));

        // Note: the component uses internal page state starting at 0,
        // so the mock with page=0 means "Previous" is always disabled at page 0 internally
        // The component's internal state is what controls this, not mock data page.
        // Since AuditLogView starts at page 0 internally via useState(0), "Previous" is always disabled on initial render.
        if (prevBtn) {
          // Component always starts at internal page 0
          expect(prevBtn.hasAttribute('disabled')).toBe(true);
        }

        if (nextBtn) {
          // Next is disabled when content.length < PAGE_SIZE
          const shouldNextBeDisabled = contentSize < PAGE_SIZE;
          expect(nextBtn.hasAttribute('disabled')).toBe(shouldNextBeDisabled);
        }
      }),
      { numRuns: 100 },
    );
  });
});

// --- Property 10: RFC 7807 Error Notification Rendering ---

/**
 * **Validates: Requirements 3.8**
 *
 * For any ProblemDetail error response containing title and detail fields,
 * the Audit Log View SHALL render a dismissible notification displaying both values.
 */
describe('Property 10: RFC 7807 Error Notification Rendering', () => {
  it('for any ApiError with title and detail, both are rendered in the notification', () => {
    const problemArb = fc.record({
      title: fc.string({ minLength: 1, maxLength: 100 }).filter(s => s.trim().length > 0),
      detail: fc.string({ minLength: 1, maxLength: 200 }).filter(s => s.trim().length > 0),
      status: fc.constantFrom(400, 404, 500, 503),
    });

    fc.assert(
      fc.property(problemArb, ({ title, detail, status }) => {
        cleanup();

        const apiError = new ApiError({ title, detail, status });

        mockUseAuditLogHistory.mockReturnValue({
          data: undefined,
          isLoading: false,
          isError: true,
          error: apiError,
        } as unknown as ReturnType<typeof useAuditLogHistory>);

        const { container } = render(<AuditLogView />);

        // Find the error alert
        const alert = container.querySelector('[role="alert"]');
        expect(alert).not.toBeNull();

        // Title should be in a bold paragraph
        const paragraphs = alert!.querySelectorAll('p');
        expect(paragraphs.length).toBeGreaterThanOrEqual(2);

        const titleElement = paragraphs[0];
        const detailElement = paragraphs[1];

        expect(titleElement.textContent).toContain(title);
        expect(detailElement.textContent).toContain(detail);
      }),
      { numRuns: 100 },
    );
  });
});

// --- Property 11: Table Column Completeness ---

/**
 * **Validates: Requirements 3.2**
 *
 * For any non-empty result set rendered in the table, each row SHALL display all 5 columns:
 * resolvedAt, podName, verdict, status badge, and modelUsed.
 */
describe('Property 11: Table Column Completeness', () => {
  it('for any non-empty result, each row renders all 5 column values', () => {
    const nonEmptyContentArb = fc.array(historyEntryArb, { minLength: 1, maxLength: 5 });

    fc.assert(
      fc.property(nonEmptyContentArb, (content: HistoryResponseDto[]) => {
        cleanup();

        const mockData: AuditLogHistoryResponse = {
          content,
          page: 0,
          size: 20,
          totalElements: content.length,
          totalPages: 1,
        };

        mockUseAuditLogHistory.mockReturnValue({
          data: mockData,
          isLoading: false,
          isError: false,
          error: null,
        } as ReturnType<typeof useAuditLogHistory>);

        const { container } = render(<AuditLogView />);

        const rows = container.querySelectorAll('tbody tr');
        expect(rows.length).toBe(content.length);

        rows.forEach((row, index) => {
          const cells = row.querySelectorAll('td');
          // 5 columns: resolvedAt, podName, verdict, action (badge), modelUsed
          expect(cells.length).toBe(5);

          const entry = content[index];

          // Column 1: resolvedAt (formatted timestamp)
          expect(cells[0].textContent).not.toBe('');

          // Column 2: podName
          expect(cells[1].textContent).toContain(entry.podName);

          // Column 3: verdict
          expect(cells[2].textContent).toContain(entry.verdict);

          // Column 4: status badge text
          expect(cells[3].textContent).toContain(entry.status);

          // Column 5: modelUsed
          expect(cells[4].textContent).toContain(entry.modelUsed);
        });
      }),
      { numRuns: 100 },
    );
  });
});
