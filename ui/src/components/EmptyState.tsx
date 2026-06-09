import type { JSX } from 'react';

export function EmptyState(): JSX.Element {
  return (
    <div
      role="status"
      className="kd-flex kd-items-center kd-justify-center kd-h-full kd-bg-surface-container kd-rounded-xl"
    >
      <p className="kd-text-on-surface-variant kd-text-body-lg">
        No analyses available yet. The system is waiting for new Kubernetes events to process.
      </p>
    </div>
  );
}

export default EmptyState;
