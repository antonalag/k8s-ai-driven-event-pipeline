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
      className="kd-col-span-12 panel-depth kd-rounded kd-flex kd-flex-col kd-overflow-hidden"
      data-testid="log-viewer-panel"
    >
      <div className="kd-flex kd-items-center kd-justify-between kd-px-4 kd-py-2 kd-border-b kd-border-outline-variant kd-bg-surface-container-high">
        <div className="kd-flex kd-items-center kd-gap-2">
          <span className="material-symbols-outlined kd-text-primary kd-text-base">
            developer_board
          </span>
          <h2 className="kd-font-sans kd-text-label-caps kd-uppercase kd-tracking-widest kd-text-on-surface">
            Console Streams — db-worker-7c2d
          </h2>
        </div>
        <div className="kd-flex kd-gap-2">
          <button
            type="button"
            className="kd-px-2.5 kd-py-1 kd-border kd-border-outline-variant kd-rounded kd-font-sans kd-text-label-caps kd-text-on-surface-variant hover:kd-border-primary hover:kd-text-primary kd-transition-colors kd-duration-200"
          >
            AUTOSCROLL
          </button>
          <button
            type="button"
            className="kd-px-2.5 kd-py-1 kd-border kd-border-outline-variant kd-rounded kd-font-sans kd-text-label-caps kd-text-on-surface-variant hover:kd-border-secondary hover:kd-text-secondary kd-transition-colors kd-duration-200"
          >
            CLEAR
          </button>
        </div>
      </div>

      <div
        className="kd-flex-1 kd-overflow-auto kd-p-4 kd-font-mono kd-text-code-sm kd-bg-surface"
        data-testid="log-content"
      >
        {logEntries.map((entry, index) => (
          <LogLine key={`${entry.timestamp}-${index}`} entry={entry} />
        ))}
      </div>
    </section>
  );
}

interface LogLineProps {
  entry: LogEntry;
}

function LogLine({ entry }: LogLineProps): JSX.Element {
  const styles = SEVERITY_STYLES[entry.severity];

  const lineClasses = styles.isHighlighted
    ? 'kd-flex kd-gap-4 kd-py-1 kd-px-3 kd-border-l-2 kd-border-secondary kd-bg-secondary/5 kd-mt-px'
    : 'kd-flex kd-gap-4 kd-py-1 kd-mt-px';

  return (
    <div className={lineClasses} data-testid="log-line">
      <span className="kd-text-on-surface-variant kd-shrink-0 kd-opacity-60">
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
            ? 'kd-text-on-surface'
            : entry.isStackTrace
              ? 'kd-text-on-surface-variant kd-opacity-60'
              : 'kd-text-on-surface-variant'
        }
      >
        {entry.message}
      </span>
    </div>
  );
}

export default LogViewer;
