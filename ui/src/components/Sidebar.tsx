import type { JSX } from 'react';

function Sidebar(): JSX.Element {
  return (
    <aside className="kd-h-screen kd-w-60 kd-fixed kd-left-0 kd-top-0 kd-bg-surface-container-low kd-border-r kd-border-outline-variant kd-flex kd-flex-col kd-z-50">
      {/* Cluster Header */}
      <div className="kd-px-5 kd-py-5 kd-border-b kd-border-outline-variant">
        <div className="kd-flex kd-items-center kd-gap-2 kd-mb-1">
          <span className="material-symbols-outlined kd-text-primary kd-text-lg">hub</span>
          <h1 className="kd-font-mono kd-text-headline-sm kd-text-on-surface kd-uppercase kd-tracking-tight">
            US-EAST-1
          </h1>
        </div>
        <p className="kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest">
          Production Cluster
        </p>
      </div>

      {/* Navigation — only Dashboard (functional) */}
      <nav className="kd-flex-1 kd-py-3">
        <div className="kd-px-5 kd-py-2 kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest kd-opacity-50">
          Navigation
        </div>
        <button
          type="button"
          className="kd-w-full kd-text-left kd-px-5 kd-py-2 kd-flex kd-items-center kd-gap-3 nav-item-active"
        >
          <span className="material-symbols-outlined kd-text-lg">dashboard</span>
          <span className="kd-font-sans kd-text-body-md kd-font-medium">Dashboard</span>
        </button>
      </nav>

      {/* Footer */}
      <div className="kd-px-5 kd-py-4 kd-border-t kd-border-outline-variant">
        <div className="kd-font-mono kd-text-code-sm kd-text-on-surface-variant kd-opacity-50">
          v0.1.0
        </div>
      </div>
    </aside>
  );
}

export default Sidebar;
