import { useState, useCallback } from 'react';
import type { JSX } from 'react';
import Sidebar from './components/Sidebar';
import TopBar from './components/TopBar';
import { AnalysisCard } from './components/AnalysisCard';
import { useAnalyses } from './api/hooks';
import { ApiError } from './api/client';
import EmptyState from './components/EmptyState';

// --- Error Display ---

function getErrorDisplay(error: Error): { title: string; detail: string } {
  if (error instanceof TypeError) {
    return { title: 'Connection Error', detail: 'Unable to reach the backend. Verify the API server is running.' };
  }
  if (error instanceof ApiError) {
    return {
      title: error.problem.title ?? 'API Error',
      detail: error.problem.detail ?? 'An unexpected error occurred.',
    };
  }
  return { title: 'API Error', detail: 'An unexpected error occurred.' };
}

// --- App ---

function App(): JSX.Element {
  const { data, isLoading, isError, error, refetch, isFetching } = useAnalyses();
  const [dismissedPods, setDismissedPods] = useState<Set<string>>(new Set());

  // Called after a successful remediation — triggers exit animation then hides the card
  const handleDismiss = useCallback((podName: string) => {
    setDismissedPods(prev => new Set(prev).add(podName));
  }, []);

  // Filter out DEGRADED and dismissed pods
  const validAnalyses = data
    ?.filter(a => a.verdict !== 'DEGRADED')
    ?.filter(a => !dismissedPods.has(a.podName))
    ?? [];

  // Breadcrumbs
  const breadcrumbs = validAnalyses.length > 0
    ? [
        { label: 'cluster-01' },
        { label: validAnalyses[0].namespace },
        { label: validAnalyses[0].podName, isActive: true },
      ]
    : [{ label: 'cluster-01' }, { label: 'default', isActive: true }];

  function renderContent(): JSX.Element {
    if (isLoading && !data) {
      return (
        <div role="status" aria-label="Loading" className="kd-flex kd-items-center kd-justify-center kd-py-20">
          <div className="kd-w-8 kd-h-8 kd-rounded-full kd-border-2 kd-border-primary kd-border-t-transparent kd-animate-spin" />
        </div>
      );
    }

    if (isError && error) {
      const { title, detail } = getErrorDisplay(error);
      return (
        <div role="alert" className="kd-flex kd-flex-col kd-items-center kd-justify-center kd-gap-4 kd-py-20">
          <h2 className="kd-font-sans kd-text-headline-sm kd-text-secondary">{title}</h2>
          <p className="kd-font-sans kd-text-body-md kd-text-on-surface-variant kd-text-center kd-max-w-md">{detail}</p>
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="kd-px-4 kd-py-2 kd-bg-on-surface kd-text-surface kd-rounded kd-font-sans kd-text-body-md kd-font-bold disabled:kd-opacity-50 hover:kd-bg-primary hover:kd-text-on-primary kd-transition-colors kd-duration-200"
          >
            Retry
          </button>
        </div>
      );
    }

    if (validAnalyses.length === 0) {
      return <EmptyState />;
    }

    return (
      <div className="kd-space-y-3">
        {validAnalyses.map((analysis) => (
          <div key={analysis.podName} className="card-enter">
            <AnalysisCard analysis={analysis} onDismiss={handleDismiss} />
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="kd-flex kd-overflow-hidden kd-font-sans kd-text-body-md kd-bg-surface kd-text-on-surface">
      <Sidebar />

      <main className="kd-ml-60 kd-flex-1 kd-flex kd-flex-col kd-h-screen kd-overflow-hidden">
        <TopBar breadcrumbs={breadcrumbs} />

        <div className="kd-flex-1 kd-p-4 kd-overflow-y-auto">
          {renderContent()}
        </div>
      </main>
    </div>
  );
}

export default App;
