import { useState, useEffect, useRef } from 'react';
import type { JSX } from 'react';
import Sidebar from './components/Sidebar';
import TopBar from './components/TopBar';
import { AnalysisCard } from './components/AnalysisCard';
import { useAnalyses } from './api/hooks';
import { ApiError } from './api/client';
import EmptyState from './components/EmptyState';
import type { AiAnalysisResponse } from './types/api';

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

interface DisplayItem {
  analysis: AiAnalysisResponse;
  exiting: boolean;
}

function App(): JSX.Element {
  const { data, isLoading, isError, error, refetch, isFetching } = useAnalyses();
  const [displayItems, setDisplayItems] = useState<DisplayItem[]>([]);
  const prevPodNamesRef = useRef<Set<string>>(new Set());

  const validAnalyses = data?.filter(a => a.verdict !== 'DEGRADED') ?? [];

  useEffect(() => {
    const currentPodNames = new Set(validAnalyses.map(a => a.podName));
    const prevPodNames = prevPodNamesRef.current;

    const removedPods = [...prevPodNames].filter(p => !currentPodNames.has(p));
    const currentItems: DisplayItem[] = validAnalyses.map(a => ({ analysis: a, exiting: false }));

    if (removedPods.length > 0) {
      // Keep removed cards temporarily with exiting flag
      const exitingItems: DisplayItem[] = removedPods
        .map(podName => displayItems.find(d => d.analysis.podName === podName))
        .filter((item): item is DisplayItem => item !== undefined)
        .map(item => ({ ...item, exiting: true }));

      setDisplayItems([...currentItems, ...exitingItems]);

      // Remove exiting cards after animation
      setTimeout(() => {
        setDisplayItems(prev => prev.filter(item => !item.exiting));
      }, 1500);
    } else {
      setDisplayItems(currentItems);
    }

    prevPodNamesRef.current = currentPodNames;
  }, [data]);

  const breadcrumbs = validAnalyses.length > 0
    ? [
        { label: validAnalyses[0].namespace },
        { label: validAnalyses[0].podName, isActive: true },
      ]
    : [{ label: 'Waiting for events...', isActive: true }];

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

    if (displayItems.length === 0) {
      return <EmptyState />;
    }

    return (
      <div className="kd-space-y-3">
        {displayItems.map((item, index) => (
          <div
            key={item.analysis.podName}
            className={item.exiting ? 'card-exit' : 'card-enter'}
            style={!item.exiting ? { animationDelay: `${index * 100}ms` } : undefined}
          >
            <AnalysisCard analysis={item.analysis} />
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
