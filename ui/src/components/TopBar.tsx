import type { JSX } from 'react';
import type { TopBarProps } from '../types/dashboard';

/**
 * Sticky top bar with breadcrumb navigation, live connection indicator,
 * time-range selector, and utility icons.
 */
function TopBar({ breadcrumbs }: TopBarProps): JSX.Element {
  return (
    <header className="kd-w-full kd-h-14 kd-sticky kd-top-0 kd-z-40 kd-bg-surface-container-low/80 kd-backdrop-blur-md kd-border-b kd-border-outline-variant kd-flex kd-justify-between kd-items-center kd-px-gutter">
      {/* Left section: Breadcrumbs + Live indicator */}
      <div className="kd-flex kd-items-center kd-gap-4">
        {/* Breadcrumb navigation */}
        <nav aria-label="Breadcrumb" className="kd-flex kd-items-center kd-gap-1.5 kd-font-medium kd-text-sm">
          {breadcrumbs.map((crumb, index) => (
            <span key={crumb.label} className="kd-flex kd-items-center kd-gap-1.5">
              {index > 0 && (
                <span className="material-symbols-outlined kd-text-sm kd-text-outline">
                  chevron_right
                </span>
              )}
              <span
                className={
                  crumb.isActive
                    ? 'kd-text-on-surface kd-font-bold'
                    : 'kd-text-on-surface-variant hover:kd-text-primary kd-cursor-pointer kd-transition-colors kd-duration-300'
                }
              >
                {crumb.label}
              </span>
            </span>
          ))}
        </nav>

        {/* Live connection indicator */}
        <div className="kd-flex kd-items-center kd-gap-2 kd-bg-primary/10 kd-px-2.5 kd-py-1 kd-rounded-full kd-border kd-border-primary/20 kd-animate-pulse-soft">
          <span className="kd-w-2 kd-h-2 kd-rounded-full kd-bg-primary kd-shadow-[0_0_8px_rgba(78,222,163,0.4)]" />
          <span className="kd-text-[10px] kd-font-bold kd-text-primary kd-tracking-widest">
            LIVE CONNECTION
          </span>
        </div>
      </div>

      {/* Right section: Time range + Utility icons */}
      <div className="kd-flex kd-items-center kd-gap-6">
        {/* Time-range selector (static display) */}
        <div className="kd-flex kd-items-center kd-gap-2 kd-bg-surface-container-highest/50 kd-px-3 kd-py-1.5 kd-rounded-lg kd-border kd-border-outline-variant kd-cursor-pointer hover:kd-bg-surface-container-highest kd-transition-all kd-duration-300 kd-group">
          <span className="material-symbols-outlined kd-text-lg group-hover:kd-scale-110 kd-transition-transform kd-duration-300">
            calendar_today
          </span>
          <span className="kd-font-label-caps kd-text-[12px]">Last 15 Minutes</span>
          <span className="material-symbols-outlined kd-text-lg group-hover:kd-translate-y-0.5 kd-transition-transform kd-duration-300">
            expand_more
          </span>
        </div>

        {/* Utility icons */}
        <div className="kd-flex kd-items-center kd-gap-4">
          <span className="material-symbols-outlined kd-text-on-surface-variant kd-cursor-pointer hover:kd-text-primary kd-transition-all kd-duration-300 hover:kd-rotate-6">
            terminal
          </span>
          <span className="material-symbols-outlined kd-text-on-surface-variant kd-cursor-pointer hover:kd-text-primary kd-transition-all kd-duration-300 hover:kd-scale-110">
            notifications
          </span>
          <span className="material-symbols-outlined kd-text-on-surface-variant kd-cursor-pointer hover:kd-text-primary kd-transition-all kd-duration-300 hover:kd-rotate-45">
            settings
          </span>
          {/* Profile avatar */}
          <div className="kd-w-8 kd-h-8 kd-rounded-full kd-bg-surface-container-highest kd-border kd-border-outline-variant kd-flex kd-items-center kd-justify-center kd-cursor-pointer hover:kd-border-primary kd-transition-all kd-duration-300 kd-overflow-hidden kd-group">
            <span className="material-symbols-outlined kd-text-lg group-hover:kd-scale-110 kd-transition-transform kd-duration-300">
              person
            </span>
          </div>
        </div>
      </div>
    </header>
  );
}

export default TopBar;
