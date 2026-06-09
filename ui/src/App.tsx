import { useState } from 'react';
import type { JSX } from 'react';
import Sidebar from './components/Sidebar';
import TopBar from './components/TopBar';
import LogViewer from './components/LogViewer';
import AIDiagnosisPanel from './components/AIDiagnosisPanel';
import { useAnalyses } from './api/hooks';
import { mapAnalysisToProps, mapToCorrelatedEvents } from './api/mappers';
import { ApiError } from './api/client';
import EmptyState from './components/EmptyState';
import type { NavItemId } from './types/dashboard';

// --- Error Display Utilities ---

const ERROR_FALLBACKS = {
  title: 'API Error',
  detail: 'An unexpected error occurred. Please retry or contact your platform team.',
  networkError: 'Unable to reach the backend service. Check your network connection or verify the API server is running.',
} as const;

function getErrorDisplay(error: Error): { title: string; detail: string } {
  if (error instanceof TypeError) {
    return { title: 'Connection Error', detail: ERROR_FALLBACKS.networkError };
  }
  if (error instanceof ApiError) {
    return {
      title: error.problem.title ?? ERROR_FALLBACKS.title,
      detail: error.problem.detail ?? ERROR_FALLBACKS.detail,
    };
  }
  return { title: ERROR_FALLBACKS.title, detail: ERROR_FALLBACKS.detail };
}

// --- App Shell ---

function App(): JSX.Element {
  const [activeNavItem, setActiveNavItem] = useState<NavItemId>('dashboard');
  const { data, isLoading, isError, error, refetch, isFetching } = useAnalyses();

  // Derive breadcrumbs from first analysis or use default
  const breadcrumbs = data && data.length > 0
    ? [
        { label: 'cluster-01' },
        { label: data[0].namespace },
        { label: data[0].podName, isActive: true },
      ]
    : [{ label: 'cluster-01' }, { label: 'default', isActive: true }];

  // Content rendering logic
  function renderContent(): JSX.Element {
    // Loading state (only on initial load, not background refetch)
    if (isLoading && !data) {
      return (
        <div
          role="status"
          aria-label="Loading analyses"
          className="kd-col-span-12 kd-row-span-6 kd-flex kd-items-center kd-justify-center"
        >
          <div className="kd-w-12 kd-h-12 kd-rounded-full kd-border-4 kd-border-primary kd-border-t-transparent kd-animate-spin" />
        </div>
      );
    }

    // Error state
    if (isError && error) {
      const { title, detail } = getErrorDisplay(error);
      return (
        <div
          role="alert"
          className="kd-col-span-12 kd-row-span-6 kd-flex kd-flex-col kd-items-center kd-justify-center kd-gap-4"
        >
          <h2 className="kd-text-error kd-text-title-lg">{title}</h2>
          <p className="kd-text-on-surface-variant kd-text-body-md kd-text-center kd-max-w-md">{detail}</p>
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="kd-px-4 kd-py-2 kd-bg-primary kd-text-on-primary kd-rounded-lg kd-text-label-lg disabled:kd-opacity-50"
          >
            Retry
          </button>
        </div>
      );
    }

    // Empty state
    if (data && data.length === 0) {
      return (
        <div className="kd-col-span-12 kd-row-span-6">
          <EmptyState />
        </div>
      );
    }

    // Data state — map first analysis to panel props
    if (data && data.length > 0) {
      const panelProps = mapAnalysisToProps(data[0]);
      const correlatedEvents = mapToCorrelatedEvents(data.slice(0, 5));

      return (
        <>
          {/* LogViewer placeholder - no real logs from API yet */}
          <div
            className="kd-col-span-12 kd-row-span-3 kd-animate-reveal"
            style={{ animationDelay: '100ms', animationFillMode: 'backwards' }}
          >
            <LogViewer entries={[]} />
          </div>

          {/* AIDiagnosisPanel with real data */}
          <div
            className="kd-col-span-12 kd-row-span-3 kd-animate-reveal"
            style={{ animationDelay: '250ms', animationFillMode: 'backwards' }}
          >
            <AIDiagnosisPanel
              problemDetail={panelProps.problemDetail}
              correlatedEvents={correlatedEvents}
              remediationCommands={panelProps.remediationCommands}
              confidence={panelProps.confidence}
            />
          </div>
        </>
      );
    }

    return <></>;
  }

  return (
    <div className="kd-flex kd-overflow-hidden kd-font-body-md kd-text-body-md">
      {/* Fixed Sidebar (left) */}
      <Sidebar activeNavItem={activeNavItem} onNavItemClick={setActiveNavItem} />

      {/* Fluid Main Area (right) */}
      <main className="kd-ml-72 kd-flex-1 kd-flex kd-flex-col kd-h-screen kd-overflow-hidden">
        {/* Sticky TopBar */}
        <TopBar breadcrumbs={breadcrumbs} />

        {/* CSS Grid Content Area: 12 columns × 6 rows */}
        <div className="kd-flex-1 kd-p-6 kd-grid kd-grid-cols-12 kd-grid-rows-6 kd-gap-6 kd-overflow-hidden">
          {renderContent()}
        </div>
      </main>
    </div>
  );
}

export default App;
