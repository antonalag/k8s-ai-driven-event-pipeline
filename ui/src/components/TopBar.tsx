import type { JSX } from 'react';
import type { TopBarProps } from '../types/dashboard';

/**
 * Sticky top bar with breadcrumb navigation and live connection indicator.
 */
function TopBar({ breadcrumbs }: TopBarProps): JSX.Element {
  return (
    <header className="kd-w-full kd-h-12 kd-sticky kd-top-0 kd-z-40 kd-bg-surface-container-low kd-border-b kd-border-outline-variant kd-flex kd-items-center kd-px-4">
      <div className="kd-flex kd-items-center kd-gap-4">
        <nav aria-label="Breadcrumb" className="kd-flex kd-items-center kd-gap-1.5 kd-font-sans kd-text-body-md">
          {breadcrumbs.map((crumb, index) => (
            <span key={crumb.label} className="kd-flex kd-items-center kd-gap-1.5">
              {index > 0 && (
                <span className="material-symbols-outlined kd-text-sm kd-text-on-surface-variant">
                  chevron_right
                </span>
              )}
              <span
                className={
                  crumb.isActive
                    ? 'kd-text-on-surface kd-font-bold'
                    : 'kd-text-on-surface-variant'
                }
              >
                {crumb.label}
              </span>
            </span>
          ))}
        </nav>

        <div className="kd-flex kd-items-center kd-gap-2 kd-px-2.5 kd-py-1 kd-border kd-border-primary kd-rounded">
          <span className="kd-w-2 kd-h-2 kd-rounded-full kd-bg-primary kd-animate-pulse-soft" />
          <span className="kd-font-sans kd-text-label-caps kd-text-primary kd-tracking-widest">
            LIVE CONNECTION
          </span>
        </div>
      </div>
    </header>
  );
}

export default TopBar;
