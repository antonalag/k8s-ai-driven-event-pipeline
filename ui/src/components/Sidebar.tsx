import type { JSX } from 'react';
import type { NavItem, ActiveResource, SidebarProps, NavItemId } from '../types/dashboard';
import { STATUS_STYLES } from '../types/dashboard';

const NAV_ITEMS: NavItem[] = [
  { id: 'dashboard', label: 'Dashboard', icon: 'dashboard' },
  { id: 'pods-nodes', label: 'Pods & Nodes', icon: 'view_module' },
  { id: 'log-explorer', label: 'Log Explorer', icon: 'description' },
  { id: 'metrics', label: 'Metrics', icon: 'analytics' },
  { id: 'distributed-traces', label: 'Distributed Traces', icon: 'timeline' },
  { id: 'ai-insight-engine', label: 'AI Insight Engine', icon: 'auto_awesome', isAiItem: true },
];

const ACTIVE_RESOURCES: ActiveResource[] = [
  { name: 'analyzer-api', status: 'HEALTHY', cpuPercent: 12, memoryUsage: '256Mi' },
  { name: 'db-worker-7c2d', status: 'CRITICAL', cpuPercent: 0.1, memoryUsage: '48Mi' },
];

function Sidebar({ activeNavItem, onNavItemClick }: SidebarProps): JSX.Element {
  return (
    <aside className="kd-h-screen kd-w-72 kd-fixed kd-left-0 kd-top-0 kd-bg-surface-container-lowest kd-border-r kd-border-outline-variant kd-flex kd-flex-col kd-z-50">
      {/* Cluster Header */}
      <div className="kd-px-6 kd-py-6 kd-border-b kd-border-outline-variant">
        <div className="kd-flex kd-items-center kd-gap-2 kd-mb-1">
          <span className="material-symbols-outlined kd-text-primary">hub</span>
          <h1 className="kd-font-display-metrics kd-text-headline-sm kd-text-on-surface kd-uppercase kd-tracking-tight">
            US-EAST-1
          </h1>
        </div>
        <p className="kd-font-label-caps kd-text-label-caps kd-text-on-surface-variant kd-opacity-70">
          Production Cluster Environment
        </p>
      </div>

      {/* Navigation */}
      <nav className="kd-flex-1 kd-py-4 kd-overflow-y-auto kd-space-y-1">
        <div className="kd-px-6 kd-py-2 kd-flex kd-items-center kd-gap-3 kd-text-on-surface-variant kd-font-label-caps kd-text-[11px] kd-uppercase kd-tracking-widest kd-opacity-50 kd-mb-1">
          Navigation
        </div>

        {NAV_ITEMS.map((item) => (
          <NavItemRow
            key={item.id}
            item={item}
            isActive={activeNavItem === item.id}
            onClick={onNavItemClick}
          />
        ))}

        {/* Active Resources */}
        <div className="kd-mt-8 kd-px-6 kd-py-4 kd-border-t kd-border-outline-variant/20">
          <div className="kd-font-label-caps kd-text-label-caps kd-text-on-surface-variant kd-mb-4 kd-uppercase kd-tracking-wider kd-opacity-50">
            Active Resources
          </div>
          <div className="kd-space-y-3">
            {ACTIVE_RESOURCES.map((resource) => (
              <ResourceCard key={resource.name} resource={resource} />
            ))}
          </div>
        </div>
      </nav>

      {/* Footer */}
      <div className="kd-p-6 kd-border-t kd-border-outline-variant kd-bg-surface-container-low/30">
        <button
          type="button"
          className="kd-w-full kd-bg-on-surface kd-text-surface kd-font-bold kd-py-2.5 kd-rounded-lg hover:kd-bg-primary hover:kd-text-on-primary kd-transition-all kd-duration-300 kd-shadow-lg active:kd-scale-[0.98] kd-flex kd-items-center kd-justify-center kd-gap-2 kd-group"
        >
          <span className="material-symbols-outlined kd-text-sm kd-transition-transform kd-duration-300 group-hover:kd-rotate-90">
            add
          </span>
          DEPLOY NEW
        </button>
        <div className="kd-mt-6 kd-flex kd-flex-col kd-gap-3">
          <span className="kd-text-on-surface-variant kd-flex kd-items-center kd-gap-3 kd-text-[13px] hover:kd-text-on-surface hover:kd-translate-x-1 kd-transition-all kd-duration-300 kd-cursor-pointer">
            <span className="material-symbols-outlined kd-text-lg">help_outline</span>
            <span>Help Center</span>
          </span>
          <span className="kd-text-on-surface-variant kd-flex kd-items-center kd-gap-3 kd-text-[13px] hover:kd-text-on-surface hover:kd-translate-x-1 kd-transition-all kd-duration-300 kd-cursor-pointer">
            <span className="material-symbols-outlined kd-text-lg">integration_instructions</span>
            <span>API Documentation</span>
          </span>
        </div>
      </div>
    </aside>
  );
}

interface NavItemRowProps {
  item: NavItem;
  isActive: boolean;
  onClick: (id: NavItemId) => void;
}

function NavItemRow({ item, isActive, onClick }: NavItemRowProps): JSX.Element {
  if (item.isAiItem) {
    return (
      <button
        type="button"
        onClick={() => onClick(item.id)}
        className={`kd-w-full kd-text-left kd-px-6 kd-py-2.5 kd-flex kd-items-center kd-gap-4 hover:kd-bg-ai-violet/10 kd-transition-all kd-duration-300 kd-group ${
          isActive ? 'nav-item-active' : 'kd-text-ai-violet'
        }`}
      >
        <span
          className="material-symbols-outlined kd-transition-transform kd-duration-300 group-hover:kd-scale-110"
          style={{ fontVariationSettings: "'FILL' 1" }}
        >
          {item.icon}
        </span>
        <span className="kd-font-medium kd-transition-transform kd-duration-300 group-hover:kd-translate-x-1.5">
          {item.label}
        </span>
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={() => onClick(item.id)}
      className={`kd-w-full kd-text-left kd-px-6 kd-py-2.5 kd-flex kd-items-center kd-gap-4 hover:kd-bg-surface-container kd-transition-all kd-duration-300 kd-group ${
        isActive ? 'nav-item-active' : 'kd-text-on-surface-variant'
      }`}
    >
      <span className="material-symbols-outlined kd-transition-transform kd-duration-300 group-hover:kd-scale-110">
        {item.icon}
      </span>
      <span className="kd-transition-all kd-duration-300 group-hover:kd-text-on-surface group-hover:kd-translate-x-1">
        {item.label}
      </span>
    </button>
  );
}

interface ResourceCardProps {
  resource: ActiveResource;
}

function ResourceCard({ resource }: ResourceCardProps): JSX.Element {
  const styles = STATUS_STYLES[resource.status];
  const isHealthy = resource.status === 'HEALTHY';

  const cardClasses = isHealthy
    ? 'kd-flex kd-flex-col kd-p-3 kd-bg-surface-container kd-rounded-lg kd-border kd-border-outline-variant/40 hover:kd-border-primary/40 kd-transition-all kd-duration-300 kd-cursor-pointer kd-group hover:kd-shadow-[0_4px_12px_-4px_rgba(78,222,163,0.2)]'
    : 'kd-flex kd-flex-col kd-p-3 hover:kd-bg-surface-container kd-rounded-lg kd-transition-all kd-duration-300 kd-border kd-border-transparent hover:kd-border-outline-variant/40 kd-cursor-pointer kd-group';

  const dotClasses = isHealthy
    ? `kd-w-2 kd-h-2 kd-rounded-full ${styles.dotColor} kd-shadow-[0_0_8px_rgba(78,222,163,0.5)]`
    : `kd-w-2 kd-h-2 kd-rounded-full ${styles.dotColor} kd-animate-pulse-soft`;

  return (
    <div className={cardClasses}>
      <div className="kd-flex kd-items-center kd-justify-between kd-mb-2">
        <div className="kd-flex kd-items-center kd-gap-2">
          <span className={dotClasses} />
          <span className="kd-font-code-sm kd-text-code-sm kd-font-bold kd-truncate">
            {resource.name}
          </span>
        </div>
        <span className={`kd-text-[10px] ${styles.labelColor} kd-font-bold`}>
          {resource.status}
        </span>
      </div>
      <div className="kd-flex kd-justify-between kd-font-display-metrics kd-text-[11px] kd-text-on-surface-variant/80">
        <span>CPU: {resource.cpuPercent}%</span>
        <span>Mem: {resource.memoryUsage}</span>
      </div>
    </div>
  );
}

export default Sidebar;
