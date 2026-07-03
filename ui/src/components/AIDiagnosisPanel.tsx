import { useState } from 'react';
import type { JSX } from 'react';
import type { AIDiagnosisPanelProps } from '../types/dashboard';

function AIDiagnosisPanel({
  problemDetail,
  correlatedEvents,
  remediationCommands,
  confidence,
}: AIDiagnosisPanelProps): JSX.Element {
  const [copyStatus, setCopyStatus] = useState<'idle' | 'copied' | 'failed'>('idle');

  const commandsText = remediationCommands.join('\n');

  async function handleCopyCommands(): Promise<void> {
    try {
      await navigator.clipboard.writeText(commandsText);
      setCopyStatus('copied');
      setTimeout(() => setCopyStatus('idle'), 2000);
    } catch {
      setCopyStatus('failed');
      setTimeout(() => setCopyStatus('idle'), 2000);
    }
  }

  const confidencePercent = `${confidence}%`;

  return (
    <section
      className="kd-col-span-12 ai-panel-depth kd-rounded ai-glow kd-flex kd-flex-col kd-overflow-hidden"
      data-testid="ai-diagnosis-panel"
    >
      {/* Header */}
      <div className="kd-flex kd-items-center kd-justify-between kd-px-4 kd-py-3 kd-border-b kd-border-ai-violet/20 kd-bg-surface-container-high">
        <div className="kd-flex kd-items-center kd-gap-3">
          <div className="kd-w-7 kd-h-7 kd-rounded kd-bg-ai-violet/15 kd-flex kd-items-center kd-justify-center">
            <span
              className="material-symbols-outlined kd-text-ai-violet kd-text-base"
              style={{ fontVariationSettings: "'FILL' 1" }}
            >
              auto_awesome
            </span>
          </div>
          <div>
            <h2 className="kd-font-sans kd-text-headline-sm kd-text-on-surface">Intelligent Diagnosis</h2>
            <p className="kd-font-sans kd-text-label-caps kd-text-ai-violet kd-uppercase kd-tracking-widest">
              AI Insight Engine v2.4
            </p>
          </div>
        </div>
        <div className="kd-flex kd-flex-col kd-items-end kd-gap-1">
          <div className="kd-flex kd-items-center kd-gap-2">
            <span className="kd-font-sans kd-text-label-caps kd-text-on-surface-variant">CONFIDENCE</span>
            <span className="kd-text-ai-violet kd-font-mono kd-font-bold kd-text-body-md" data-testid="confidence-value">
              {confidencePercent}
            </span>
          </div>
          <div className="kd-w-40 kd-h-1 kd-bg-outline-variant kd-rounded-full kd-overflow-hidden">
            <div
              className="kd-h-full kd-bg-ai-violet"
              style={{ width: confidencePercent }}
              data-testid="confidence-bar"
            />
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="kd-flex-1 kd-p-4 kd-grid kd-grid-cols-12 kd-gap-4 kd-overflow-auto">
        {/* Root Cause / Problem Detail */}
        <div className="kd-col-span-7 kd-space-y-4">
          {/* Problem Detail (RFC 7807) */}
          <div className="kd-space-y-2">
            <div className="kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest kd-flex kd-items-center kd-gap-2">
              <span className="material-symbols-outlined kd-text-sm">error_outline</span>
              Problem Detail (RFC 7807)
            </div>
            <div className="kd-bg-surface kd-p-4 kd-border kd-border-outline-variant kd-rounded">
              <div className="kd-space-y-2 kd-font-mono kd-text-code-sm">
                <div className="kd-grid kd-grid-cols-[80px_1fr] kd-gap-x-3 kd-items-baseline">
                  <span className="kd-text-on-surface-variant">Type</span>
                  <span className="kd-text-ai-violet kd-truncate" data-testid="problem-type">
                    {problemDetail.type}
                  </span>
                </div>
                <div className="kd-grid kd-grid-cols-[80px_1fr] kd-gap-x-3 kd-items-baseline">
                  <span className="kd-text-on-surface-variant">Issue</span>
                  <span className="kd-text-on-surface kd-font-bold" data-testid="problem-issue">
                    {problemDetail.issue}
                  </span>
                </div>
                <div className="kd-grid kd-grid-cols-[80px_1fr] kd-gap-x-3 kd-items-baseline">
                  <span className="kd-text-on-surface-variant">Status</span>
                  <span className="kd-text-secondary kd-font-bold" data-testid="problem-status">
                    {problemDetail.status}
                  </span>
                </div>
                <div className="kd-mt-2 kd-pt-2 kd-border-t kd-border-outline-variant">
                  <p className="kd-font-sans kd-text-body-md kd-text-on-surface-variant kd-leading-relaxed" data-testid="problem-description">
                    {problemDetail.description}
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Correlated Events */}
          <div className="kd-space-y-2">
            <div className="kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest kd-flex kd-items-center kd-gap-2">
              <span className="material-symbols-outlined kd-text-sm">hub</span>
              Correlated Infrastructure Events
            </div>
            <div className="kd-space-y-1" data-testid="correlated-events">
              {correlatedEvents.map((event, index) => (
                <div
                  key={`${event.timeAgo}-${index}`}
                  className="kd-flex kd-items-center kd-gap-3 kd-px-3 kd-py-2 kd-border-b kd-border-outline-variant last:kd-border-b-0 hover:kd-bg-surface-container-high kd-transition-colors kd-duration-200"
                >
                  <span
                    className={`kd-font-mono kd-text-label-caps kd-font-bold kd-shrink-0 ${
                      event.severity === 'error'
                        ? 'kd-text-secondary'
                        : event.severity === 'warning'
                          ? 'kd-text-tertiary'
                          : 'kd-text-ai-violet'
                    }`}
                    data-testid="event-time-badge"
                  >
                    {event.timeAgo}
                  </span>
                  <span className="kd-font-sans kd-text-body-md kd-text-on-surface-variant">{event.description}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Remediation Commands */}
        <div className="kd-col-span-5 kd-flex kd-flex-col kd-gap-3">
          <div className="kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest kd-flex kd-items-center kd-gap-2">
            <span className="material-symbols-outlined kd-text-sm">magic_button</span>
            Guided Remediation Path
          </div>

          {/* Code block — Zinc-950 bg, code-sm */}
          <pre
            className="kd-flex-1 kd-bg-surface kd-border kd-border-outline-variant kd-p-4 kd-rounded kd-font-mono kd-text-code-sm kd-text-on-surface-variant kd-overflow-auto kd-whitespace-pre-wrap"
            data-testid="remediation-commands"
          >
            {commandsText}
          </pre>

          {/* Copy button — secondary/ghost style */}
          <button
            type="button"
            onClick={handleCopyCommands}
            className="kd-w-full kd-py-2 kd-border kd-border-outline-variant kd-rounded kd-font-sans kd-text-body-md kd-font-medium kd-text-on-surface-variant hover:kd-border-ai-violet hover:kd-text-ai-violet kd-transition-colors kd-duration-200 kd-flex kd-items-center kd-justify-center kd-gap-2"
            data-testid="copy-commands-button"
          >
            <span className="material-symbols-outlined kd-text-sm">content_copy</span>
            <span>
              {copyStatus === 'copied'
                ? 'Copied!'
                : copyStatus === 'failed'
                  ? 'Copy failed'
                  : 'Copy Commands'}
            </span>
          </button>
        </div>
      </div>
    </section>
  );
}

export default AIDiagnosisPanel;
