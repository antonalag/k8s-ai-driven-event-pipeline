import type { JSX } from 'react';
import type { NavItemId, SidebarProps } from '../types/dashboard';

interface NavItemConfig {
  id: NavItemId;
  label: string;
  icon: string;
  isAiItem?: boolean;
}

const NAV_ITEMS: NavItemConfig[] = [
  { id: 'dashboard', label: 'Dashboard', icon: 'dashboard' },
  { id: 'log-explorer', label: 'Log Explorer', icon: 'description' },
  { id: 'ai-insight-engine', label: 'AI Insight Engine', icon: 'auto_awesome', isAiItem: true },
];

function Sidebar({ activeNavItem, onNavItemClick }: SidebarProps): JSX.Element {
  return (
    <aside className="kd-h-screen kd-w-60 kd-fixed kd-left-0 kd-top-0 kd-bg-surface-container-low kd-border-r kd-border-outline-variant kd-flex kd-flex-col kd-z-50">
      <div className="kd-px-5 kd-py-5 kd-border-b kd-border-outline-variant">
        <div className="kd-flex kd-items-center kd-gap-2 kd-mb-1">
          <span className="material-symbols-outlined kd-text-primary kd-text-lg">hub</span>
          <h1 className="kd-font-mono kd-text-headline-sm kd-text-on-surface kd-uppercase kd-tracking-tight">
            K8s Pipeline
          </h1>
        </div>
        <p className="kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest">
          AI-Driven Observability
        </p>
      </div>

      <nav className="kd-flex-1 kd-py-3">
        {NAV_ITEMS.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => onNavItemClick(item.id)}
            className={`kd-w-full kd-text-left kd-px-5 kd-py-2 kd-flex kd-items-center kd-gap-3 kd-group kd-transition-colors ${
              activeNavItem === item.id ? 'nav-item-active' : ''
            }`}
          >
            <span className="material-symbols-outlined kd-text-lg kd-transition-transform group-hover:kd-scale-110">
              {item.icon}
            </span>
            <span className={`kd-font-sans kd-text-body-md kd-font-medium kd-transition-transform ${
              !item.isAiItem ? 'group-hover:kd-translate-x-1' : ''
            }`}>
              {item.label}
            </span>
          </button>
        ))}
      </nav>

      <div className="kd-px-5 kd-py-4 kd-border-t kd-border-outline-variant">
        <p className="kd-font-sans kd-text-label-caps kd-text-on-surface-variant kd-uppercase kd-tracking-widest kd-mb-3">
          Active Resources
        </p>
        <div className="kd-space-y-2">
          <div className="kd-p-3 kd-rounded-lg kd-border kd-border-outline-variant kd-transition-all hover:kd-border-primary/40 hover:kd-shadow">
            <div className="kd-flex kd-items-center kd-gap-2 kd-mb-1">
              <span className="kd-w-2 kd-h-2 kd-rounded-full kd-bg-primary"></span>
              <span className="kd-font-mono kd-text-body-sm kd-text-on-surface">analyzer-api</span>
            </div>
            <div className="kd-flex kd-items-center kd-gap-2">
              <span className="kd-text-label-caps kd-text-primary">HEALTHY</span>
              <span className="kd-text-label-caps kd-text-on-surface-variant">CPU: 12%</span>
              <span className="kd-text-label-caps kd-text-on-surface-variant">Mem: 256Mi</span>
            </div>
          </div>
          <div className="kd-p-3 kd-rounded-lg kd-border kd-border-outline-variant kd-transition-all hover:kd-border-error/40 hover:kd-shadow">
            <div className="kd-flex kd-items-center kd-gap-2 kd-mb-1">
              <span className="kd-w-2 kd-h-2 kd-rounded-full kd-bg-error"></span>
              <span className="kd-font-mono kd-text-body-sm kd-text-on-surface">db-worker-7c2d</span>
            </div>
            <div className="kd-flex kd-items-center kd-gap-2">
              <span className="kd-text-label-caps kd-text-error">CRITICAL</span>
              <span className="kd-text-label-caps kd-text-on-surface-variant">CPU: 0.1%</span>
              <span className="kd-text-label-caps kd-text-on-surface-variant">Mem: 48Mi</span>
            </div>
          </div>
        </div>
      </div>
    </aside>
  );
}

export default Sidebar;
