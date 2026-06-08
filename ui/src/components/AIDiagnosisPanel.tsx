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
      className="kd-col-span-12 kd-row-span-3 ai-panel-depth kd-rounded-xl ai-glow kd-flex kd-flex-col kd-overflow-hidden kd-relative"
      data-testid="ai-diagnosis-panel"
    >
      {/* Violet gradient overlay */}
      <div className="kd-absolute kd-inset-0 kd-bg-gradient-to-br kd-from-ai-violet/10 kd-via-transparent kd-to-transparent kd-pointer-events-none" />

      {/* Header */}
      <div className="kd-flex kd-items-center kd-justify-between kd-px-5 kd-py-4 kd-border-b kd-border-white/5 kd-bg-white/5 kd-relative kd-z-10">
        <div className="kd-flex kd-items-center kd-gap-3">
          <div className="kd-w-8 kd-h-8 kd-rounded-lg kd-bg-ai-violet/20 kd-flex kd-items-center kd-justify-center kd-transition-all kd-duration-500 kd-group">
            <span
              className="material-symbols-outlined kd-text-ai-violet"
              style={{ fontVariationSettings: "'FILL' 1" }}
            >
              auto_awesome
            </span>
          </div>
          <div>
            <h2 className="kd-font-headline-sm kd-text-on-surface">Intelligent Diagnosis</h2>
            <p className="kd-text-[11px] kd-text-ai-violet/80 kd-font-medium kd-tracking-wide kd-uppercase">
              AI Insight Engine v2.4
            </p>
          </div>
        </div>
        <div className="kd-flex kd-flex-col kd-items-end kd-gap-1">
          <div className="kd-flex kd-items-center kd-gap-3">
            <span className="kd-text-[11px] kd-font-bold kd-text-zinc-400">ANALYSIS CONFIDENCE</span>
            <span className="kd-text-ai-violet kd-font-bold kd-text-sm" data-testid="confidence-value">
              {confidencePercent}
            </span>
          </div>
          <div className="kd-w-48 kd-h-1.5 kd-bg-zinc-800 kd-rounded-full kd-overflow-hidden shimmer-mask">
            <div
              className="kd-h-full kd-bg-ai-violet kd-shadow-[0_0_10px_rgba(167,139,250,0.5)]"
              style={{ width: confidencePercent }}
              data-testid="confidence-bar"
            />
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="kd-flex-1 kd-p-6 kd-grid kd-grid-cols-12 kd-gap-8 kd-relative kd-z-10 kd-overflow-auto">
        {/* Root Cause / Problem Detail */}
        <div className="kd-col-span-7 kd-space-y-6">
          {/* Problem Detail (RFC 7807) */}
          <div className="kd-space-y-3">
            <div className="kd-flex kd-items-center kd-gap-2 kd-text-zinc-400 kd-font-label-caps kd-text-[11px] kd-uppercase kd-tracking-widest">
              <span className="material-symbols-outlined kd-text-sm">error_outline</span>
              <span>Problem Detail (RFC 7807)</span>
            </div>
            <div className="kd-bg-black/40 kd-p-4 kd-border kd-border-white/5 kd-rounded-xl kd-transition-all kd-duration-500 hover:kd-border-white/10 hover:kd-bg-black/50">
              <div className="kd-space-y-3">
                <div className="kd-grid kd-grid-cols-[100px_1fr] kd-gap-x-4 kd-items-baseline">
                  <span className="kd-text-zinc-500 kd-font-code-sm kd-text-[12px]">Type</span>
                  <span className="kd-text-ai-violet kd-font-code-sm kd-text-[12px] kd-truncate" data-testid="problem-type">
                    {problemDetail.type}
                  </span>
                </div>
                <div className="kd-grid kd-grid-cols-[100px_1fr] kd-gap-x-4 kd-items-baseline">
                  <span className="kd-text-zinc-500 kd-font-code-sm kd-text-[12px]">Issue</span>
                  <span className="kd-text-on-surface kd-font-bold" data-testid="problem-issue">
                    {problemDetail.issue}
                  </span>
                </div>
                <div className="kd-grid kd-grid-cols-[100px_1fr] kd-gap-x-4 kd-items-baseline">
                  <span className="kd-text-zinc-500 kd-font-code-sm kd-text-[12px]">Status</span>
                  <span className="kd-text-error kd-font-bold kd-flex kd-items-center kd-gap-1.5" data-testid="problem-status">
                    <span className="material-symbols-outlined kd-text-sm kd-animate-pulse-soft">wifi_off</span>
                    {problemDetail.status}
                  </span>
                </div>
                <div className="kd-mt-3 kd-pt-3 kd-border-t kd-border-white/5">
                  <p className="kd-text-on-surface-variant kd-leading-relaxed kd-text-sm" data-testid="problem-description">
                    {problemDetail.description}
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Correlated Infrastructure Events */}
          <div className="kd-space-y-3">
            <div className="kd-flex kd-items-center kd-gap-2 kd-text-zinc-400 kd-font-label-caps kd-text-[11px] kd-uppercase kd-tracking-widest">
              <span className="material-symbols-outlined kd-text-sm">hub</span>
              <span>Correlated Infrastructure Events</span>
            </div>
            <div className="kd-grid kd-grid-cols-1 kd-gap-2" data-testid="correlated-events">
              {correlatedEvents.map((event, index) => (
                <div
                  key={`${event.timeAgo}-${index}`}
                  className="kd-flex kd-items-center kd-gap-4 kd-px-4 kd-py-2.5 kd-bg-zinc-800/30 kd-border kd-border-white/5 kd-rounded-lg kd-transition-all kd-duration-300 hover:kd-bg-zinc-800/50 hover:kd-translate-x-1 kd-cursor-default"
                >
                  <span
                    className={`kd-text-[10px] kd-px-2 kd-py-0.5 kd-rounded kd-font-bold ${
                      event.severity === 'error'
                        ? 'kd-bg-error/20 kd-text-error'
                        : event.severity === 'warning'
                          ? 'kd-bg-tertiary/20 kd-text-tertiary'
                          : 'kd-bg-ai-violet/20 kd-text-ai-violet'
                    }`}
                    data-testid="event-time-badge"
                  >
                    {event.timeAgo}
                  </span>
                  <span className="kd-text-sm kd-text-zinc-300">{event.description}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Remediation Commands */}
        <div className="kd-col-span-5 kd-flex kd-flex-col kd-gap-4">
          <div className="kd-flex kd-items-center kd-gap-2 kd-text-zinc-400 kd-font-label-caps kd-text-[11px] kd-uppercase kd-tracking-widest kd-mb-1">
            <span className="material-symbols-outlined kd-text-sm">magic_button</span>
            <span>Guided Remediation Path</span>
          </div>

          <pre
            className="kd-bg-zinc-900/80 kd-border kd-border-white/10 kd-p-4 kd-rounded-xl kd-font-code-sm kd-text-code-sm kd-text-on-surface kd-overflow-auto kd-whitespace-pre-wrap"
            data-testid="remediation-commands"
          >
            {commandsText}
          </pre>

          <div className="kd-mt-auto kd-pt-4 kd-border-t kd-border-white/5">
            <button
              type="button"
              onClick={handleCopyCommands}
              className="kd-w-full kd-py-2.5 kd-bg-surface-container-lowest kd-border kd-border-outline-variant kd-text-on-surface-variant kd-font-bold kd-rounded-xl hover:kd-border-ai-violet/50 hover:kd-text-ai-violet kd-transition-all kd-duration-300 kd-flex kd-items-center kd-justify-center kd-gap-2"
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
      </div>
    </section>
  );
}

export default AIDiagnosisPanel;
