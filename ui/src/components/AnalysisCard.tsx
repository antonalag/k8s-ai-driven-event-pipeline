import type { JSX } from 'react';
import type { AiAnalysisResponse } from '../types/api';
import { RecommendedActionBlock } from './RecommendedActionBlock';

interface AnalysisCardProps {
  analysis: AiAnalysisResponse;
}

function generateCorrelationId(): string {
  return crypto.randomUUID();
}

const VERDICT_STYLES: Record<string, { border: string; badge: string; label: string }> = {
  CRITICAL_FAILURE: {
    border: 'kd-border-secondary',
    badge: 'kd-text-secondary kd-border-secondary',
    label: 'CRITICAL',
  },
  TRANSIENT_ISSUE: {
    border: 'kd-border-tertiary',
    badge: 'kd-text-tertiary kd-border-tertiary',
    label: 'WARNING',
  },
  HEALTHY: {
    border: 'kd-border-primary',
    badge: 'kd-text-primary kd-border-primary',
    label: 'HEALTHY',
  },
  DEGRADED: {
    border: 'kd-border-outline-variant',
    badge: 'kd-text-on-surface-variant kd-border-outline-variant',
    label: 'DEGRADED',
  },
};

function getVerdictStyle(verdict: string) {
  return VERDICT_STYLES[verdict] ?? VERDICT_STYLES['DEGRADED'];
}

function formatTime(isoTimestamp: string): string {
  try {
    return new Date(isoTimestamp).toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return '—';
  }
}

export function AnalysisCard({ analysis }: AnalysisCardProps): JSX.Element {
  const style = getVerdictStyle(analysis.verdict);

  return (
    <div className={`kd-border ${style.border} kd-rounded kd-bg-surface-container-low kd-overflow-hidden`}>
      <div className="kd-flex kd-items-center kd-justify-between kd-px-4 kd-py-3 kd-border-b kd-border-outline-variant kd-bg-surface-container-high">
        <div className="kd-flex kd-items-center kd-gap-3">
          <span className="kd-font-mono kd-text-code-sm kd-text-on-surface kd-font-bold">
            {analysis.podName}
          </span>
          <span className="kd-font-mono kd-text-code-sm kd-text-on-surface-variant">
            {analysis.namespace}
          </span>
        </div>
        <div className="kd-flex kd-items-center kd-gap-3">
          <span className="kd-font-mono kd-text-code-sm kd-text-on-surface-variant">
            {formatTime(analysis.analyzedAt)}
          </span>
          <span className={`kd-font-sans kd-text-label-caps kd-font-bold kd-px-2 kd-py-0.5 kd-border kd-rounded-sm kd-flex kd-items-center kd-gap-1.5 ${style.badge}`}>
            <span className="kd-w-1.5 kd-h-1.5 kd-rounded-full kd-bg-current" />
            {style.label}
          </span>
        </div>
      </div>

      <div className="kd-px-4 kd-py-3 kd-space-y-3">
        <div>
          <div className="kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest kd-mb-1">
            Root Cause
          </div>
          <p className="kd-font-sans kd-text-body-md kd-text-on-surface kd-leading-relaxed">
            {analysis.rootCauseAnalysis}
          </p>
        </div>

        {analysis.recommendedActions && analysis.recommendedActions.length > 0 && (
          <div>
            <div className="kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest kd-mb-2">
              Recommended Actions
            </div>
            <RecommendedActionBlock
              actions={analysis.recommendedActions}
              correlationId={generateCorrelationId()}
              namespace={analysis.namespace}
            />
          </div>
        )}
      </div>
    </div>
  );
}
