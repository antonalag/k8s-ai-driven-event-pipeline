import type { JSX } from 'react';

export function EmptyState(): JSX.Element {
  return (
    <div
      role="status"
      className="kd-flex kd-flex-col kd-items-center kd-justify-center kd-h-full kd-border kd-border-outline-variant kd-rounded kd-bg-surface-container-low"
    >
      <span className="material-symbols-outlined kd-text-4xl kd-text-on-surface-variant kd-opacity-40 kd-mb-3">
        hourglass_empty
      </span>
      <p className="kd-font-sans kd-text-body-md kd-text-on-surface-variant kd-text-center kd-max-w-sm">
        No analyses available yet. The system is waiting for new Kubernetes events to process.
      </p>
    </div>
  );
}

export default EmptyState;
