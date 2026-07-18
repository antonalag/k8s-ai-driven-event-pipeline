import { useState } from 'react';
import type { JSX } from 'react';
import { useAuditLogHistory } from '../hooks/useAuditLogHistory';
import type { HistoryResponseDto, ResolvedStatus } from '../types/api';
import { ApiError } from '../api/client';

const PAGE_SIZE = 20;

function StatusBadge({ status }: { status: ResolvedStatus }): JSX.Element {
  const colors = status === 'REMEDIATED'
    ? 'kd-bg-primary/10 kd-text-primary'
    : 'kd-bg-tertiary/10 kd-text-tertiary';
  return (
    <span className={`kd-px-2 kd-py-0.5 kd-rounded kd-text-label-caps kd-font-bold ${colors}`}>
      {status}
    </span>
  );
}

function formatTimestamp(isoTimestamp: string): string {
  try {
    return new Date(isoTimestamp).toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return '—';
  }
}

function SkeletonRows(): JSX.Element {
  return (
    <>
      {Array.from({ length: 5 }).map((_, i) => (
        <tr key={i} className="kd-border-b kd-border-outline-variant">
          <td className="kd-px-4 kd-py-3"><div className="kd-h-4 kd-w-36 kd-bg-on-surface/10 kd-rounded kd-animate-pulse" /></td>
          <td className="kd-px-4 kd-py-3"><div className="kd-h-4 kd-w-40 kd-bg-on-surface/10 kd-rounded kd-animate-pulse" /></td>
          <td className="kd-px-4 kd-py-3"><div className="kd-h-4 kd-w-32 kd-bg-on-surface/10 kd-rounded kd-animate-pulse" /></td>
          <td className="kd-px-4 kd-py-3"><div className="kd-h-4 kd-w-24 kd-bg-on-surface/10 kd-rounded kd-animate-pulse" /></td>
          <td className="kd-px-4 kd-py-3"><div className="kd-h-4 kd-w-28 kd-bg-on-surface/10 kd-rounded kd-animate-pulse" /></td>
        </tr>
      ))}
    </>
  );
}

export function AuditLogView(): JSX.Element {
  const [page, setPage] = useState(0);
  const [dismissedError, setDismissedError] = useState(false);
  const { data, isLoading, isError, error } = useAuditLogHistory({ page, size: PAGE_SIZE });

  const canPrevious = page > 0;
  const canNext = data ? data.content.length >= PAGE_SIZE : false;
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="kd-flex kd-flex-col kd-h-full kd-gap-4">
      <h2 className="kd-font-sans kd-text-headline-sm kd-text-on-surface">Audit Log</h2>

      {/* Error notification */}
      {isError && error && !dismissedError && (
        <div role="alert" className="kd-flex kd-items-start kd-gap-3 kd-p-4 kd-rounded kd-border kd-border-secondary kd-bg-secondary/10">
          <span className="material-symbols-outlined kd-text-secondary kd-text-lg">error</span>
          <div className="kd-flex-1">
            <p className="kd-font-sans kd-text-body-md kd-font-bold kd-text-secondary">
              {error instanceof ApiError ? (error.problem.title ?? 'Error') : 'Connection Error'}
            </p>
            <p className="kd-font-sans kd-text-body-md kd-text-on-surface-variant">
              {error instanceof ApiError
                ? (error.problem.detail ?? 'An unexpected error occurred.')
                : 'Unable to reach the backend. Verify the API server is running.'}
            </p>
          </div>
          <button
            type="button"
            onClick={() => setDismissedError(true)}
            className="kd-p-1 kd-rounded hover:kd-bg-on-surface/10 kd-transition-colors"
            aria-label="Dismiss error"
          >
            <span className="material-symbols-outlined kd-text-on-surface-variant kd-text-lg">close</span>
          </button>
        </div>
      )}

      {/* Empty state */}
      {!isLoading && !isError && data && data.content.length === 0 && (
        <div
          role="status"
          className="kd-flex kd-flex-col kd-items-center kd-justify-center kd-py-20 kd-border kd-border-outline-variant kd-rounded kd-bg-surface-container-low"
        >
          <span className="material-symbols-outlined kd-text-4xl kd-text-on-surface-variant kd-opacity-40 kd-mb-3">
            history
          </span>
          <p className="kd-font-sans kd-text-body-md kd-text-on-surface-variant kd-text-center kd-max-w-sm">
            No resolved analyses yet
          </p>
        </div>
      )}

      {/* Table */}
      {(isLoading || (data && data.content.length > 0)) && (
        <div className="kd-flex-1 kd-overflow-auto kd-border kd-border-outline-variant kd-rounded kd-bg-surface-container-low">
          <table className="kd-w-full kd-border-collapse">
            <thead className="kd-sticky kd-top-0 kd-bg-surface-container-high kd-z-10">
              <tr className="kd-border-b kd-border-outline-variant">
                <th className="kd-px-4 kd-py-3 kd-text-left kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest">
                  Resolved At
                </th>
                <th className="kd-px-4 kd-py-3 kd-text-left kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest">
                  Pod Name
                </th>
                <th className="kd-px-4 kd-py-3 kd-text-left kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest">
                  Verdict
                </th>
                <th className="kd-px-4 kd-py-3 kd-text-left kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest">
                  Action
                </th>
                <th className="kd-px-4 kd-py-3 kd-text-left kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest">
                  LLM Model
                </th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <SkeletonRows />
              ) : (
                data?.content.map((entry: HistoryResponseDto, index: number) => (
                  <tr key={`${entry.podName}-${entry.resolvedAt}-${index}`} className="kd-border-b kd-border-outline-variant hover:kd-bg-on-surface/5 kd-transition-colors">
                    <td className="kd-px-4 kd-py-3 kd-font-mono kd-text-code-sm kd-text-on-surface">
                      {formatTimestamp(entry.resolvedAt)}
                    </td>
                    <td className="kd-px-4 kd-py-3 kd-font-mono kd-text-code-sm kd-text-on-surface kd-font-bold">
                      {entry.podName}
                    </td>
                    <td className="kd-px-4 kd-py-3 kd-font-sans kd-text-body-md kd-text-on-surface-variant">
                      {entry.verdict}
                    </td>
                    <td className="kd-px-4 kd-py-3">
                      <StatusBadge status={entry.status} />
                    </td>
                    <td className="kd-px-4 kd-py-3 kd-font-mono kd-text-code-sm kd-text-on-surface-variant">
                      {entry.modelUsed}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination controls */}
      {data && data.content.length > 0 && (
        <div className="kd-flex kd-items-center kd-justify-between kd-px-1">
          <button
            type="button"
            disabled={!canPrevious}
            onClick={() => setPage((p) => p - 1)}
            className="kd-flex kd-items-center kd-gap-1 kd-px-3 kd-py-1.5 kd-rounded kd-font-sans kd-text-body-md kd-font-medium kd-border kd-border-outline-variant kd-text-on-surface hover:kd-bg-on-surface/10 kd-transition-colors disabled:kd-opacity-40 disabled:kd-cursor-not-allowed disabled:hover:kd-bg-transparent"
          >
            <span className="material-symbols-outlined kd-text-lg">chevron_left</span>
            Previous
          </button>

          <span className="kd-font-sans kd-text-body-md kd-text-on-surface-variant">
            Page {page + 1} of {totalPages}
          </span>

          <button
            type="button"
            disabled={!canNext}
            onClick={() => setPage((p) => p + 1)}
            className="kd-flex kd-items-center kd-gap-1 kd-px-3 kd-py-1.5 kd-rounded kd-font-sans kd-text-body-md kd-font-medium kd-border kd-border-outline-variant kd-text-on-surface hover:kd-bg-on-surface/10 kd-transition-colors disabled:kd-opacity-40 disabled:kd-cursor-not-allowed disabled:hover:kd-bg-transparent"
          >
            Next
            <span className="material-symbols-outlined kd-text-lg">chevron_right</span>
          </button>
        </div>
      )}
    </div>
  );
}
