import { useState } from 'react';
import type { JSX } from 'react';
import type { LogEntry, LogViewerProps } from '../types/dashboard';
import { SEVERITY_STYLES } from '../types/dashboard';

const MOCK_LOG_ENTRIES: LogEntry[] = [
  { timestamp: '14:02:11.231', severity: 'INFO', message: 'Worker pool initialized with 4 threads. Threads healthy and responsive.' },
  { timestamp: '14:02:12.455', severity: 'INFO', message: 'Attempting connection to postgresql-master.svc.cluster.local:5432' },
  { timestamp: '14:02:15.982', severity: 'ERROR', message: 'ConnectionTimeoutException: Unable to establish socket to host.' },
  { timestamp: '14:02:16.001', severity: 'ERROR', message: 'java.net.ConnectException: Connection refused (Connection refused)', isStackTrace: true },
  { timestamp: '14:02:21.110', severity: 'WARN', message: 'Retrying connection in 5000ms... (Attempt 3/5)' },
  { timestamp: '14:02:26.241', severity: 'CRIT', message: 'Liveness probe failed. Container marked as unhealthy by kube-scheduler.' },
];

function LogViewer({ entries }: LogViewerProps): JSX.Element {
  const logEntries = entries.length > 0 ? entries : MOCK_LOG_ENTRIES;

  return (
    <section
      className="kd-col-span-12 kd-row-span-3 panel-depth kd-rounded-xl kd-flex kd-flex-col kd-overflow-hidden kd-shadow-xl"
      data-testid="log-viewer-panel"
    >
      {/* Panel Header */}
      <div className="kd-flex kd-items-center kd-justify-between kd-px-4 kd-py-3 kd-border-b kd-border-outline-variant kd-bg-surface-container-high/50">
        <div className="kd-flex kd-items-center kd-gap-3">
          <span className="material-symbols-outlined kd-text-primary kd-animate-pulse-soft">
            developer_board
          </span>
          <h2 className="kd-font-headline-sm kd-text-sm kd-uppercase kd-tracking-wider kd-text-on-surface">
            Console Streams — db-worker-7c2d
          </h2>
        </div>
        <div className="kd-flex kd-gap-2">
          <button
            type="button"
            className="kd-px-3 kd-py-1 kd-bg-surface-container-lowest kd-border kd-border-outline-variant kd-rounded kd-font-label-caps kd-text-[11px] hover:kd-border-primary hover:kd-text-primary kd-transition-all kd-duration-300"
          >
            AUTOSCROLL
          </button>
          <button
            type="button"
            className="kd-px-3 kd-py-1 kd-bg-surface-container-lowest kd-border kd-border-outline-variant kd-rounded kd-font-label-caps kd-text-[11px] hover:kd-bg-error/10 hover:kd-border-error/40 hover:kd-text-error kd-transition-all kd-duration-300"
          >
            CLEAR
          </button>
        </div>
      </div>

      {/* Log Content */}
      <div
        className="kd-flex-1 kd-overflow-auto kd-p-5 kd-font-code-sm kd-text-code-sm kd-leading-relaxed kd-bg-black/30 selection:kd-bg-primary/20 kd-animate-flicker"
        data-testid="log-content"
      >
        {logEntries.map((entry, index) => (
          <LogLine key={`${entry.timestamp}-${index}`} entry={entry} isFirst={index === 0} />
        ))}
      </div>
    </section>
  );
}

interface LogLineProps {
  entry: LogEntry;
  isFirst: boolean;
}

function LogLine({ entry, isFirst }: LogLineProps): JSX.Element {
  const [glowActive, setGlowActive] = useState(false);
  const styles = SEVERITY_STYLES[entry.severity];

  const lineClasses = styles.isHighlighted
    ? `kd-flex kd-gap-6 kd-bg-error/5 kd-border-l-4 kd-border-error kd-px-3 kd-py-1 kd-rounded-r ${isFirst ? '' : 'kd-mt-2'}`
    : `kd-flex kd-gap-6 ${isFirst ? '' : 'kd-mt-1'}`;

  const glowStyle = glowActive
    ? { textShadow: '0 0 12px rgba(78, 222, 163, 0.4)', transition: 'text-shadow 300ms' }
    : { textShadow: 'none', transition: 'text-shadow 300ms' };

  return (
    <div
      className={lineClasses}
      style={glowStyle}
      onMouseEnter={() => setGlowActive(true)}
      onMouseLeave={() => setGlowActive(false)}
      data-testid="log-line"
    >
      <span className="kd-text-zinc-600 kd-shrink-0 kd-font-mono kd-text-[11px] kd-pt-0.5">
        {entry.timestamp}
      </span>
      {!entry.isStackTrace && (
        <span className={`${styles.tagColor} kd-shrink-0 kd-font-bold`} data-testid="severity-tag">
          [{entry.severity}]
        </span>
      )}
      <span
        className={
          styles.isHighlighted
            ? 'kd-text-on-error-container'
            : entry.isStackTrace
              ? 'kd-text-zinc-500'
              : 'kd-text-on-surface-variant'
        }
      >
        {entry.message}
      </span>
    </div>
  );
}

export default LogViewer;
