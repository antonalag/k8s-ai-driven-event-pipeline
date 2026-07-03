/**
 * Dashboard-specific types, interfaces, and style constant mappings
 * for the Observability Dashboard integration layer.
 */

// --- Navigation Types ---

export type NavItemId =
  | 'dashboard'
  | 'log-explorer'
  | 'ai-insight-engine';

export interface NavItem {
  id: NavItemId;
  label: string;
  icon: string;
  isAiItem?: boolean;
}

// --- Log Types ---

export type LogSeverity = 'INFO' | 'WARN' | 'ERROR' | 'CRIT';

export interface LogEntry {
  timestamp: string;
  severity: LogSeverity;
  message: string;
  isStackTrace?: boolean;
}

// --- Resource Types ---

export interface ActiveResource {
  name: string;
  status: 'HEALTHY' | 'CRITICAL' | 'WARNING';
  cpuPercent: number;
  memoryUsage: string;
}

// --- AI Diagnosis Types ---

export interface CorrelatedEvent {
  timeAgo: string;
  description: string;
  severity: 'info' | 'error' | 'warning';
}

export interface ProblemDetailDisplay {
  type: string;
  issue: string;
  status: string;
  description: string;
}

// --- Component Props ---

export interface SidebarProps {
  activeNavItem: NavItemId;
  onNavItemClick: (id: NavItemId) => void;
}

export interface TopBarProps {
  breadcrumbs: { label: string; isActive?: boolean }[];
}

export interface LogViewerProps {
  entries: LogEntry[];
}

export interface AIDiagnosisPanelProps {
  problemDetail: ProblemDetailDisplay;
  correlatedEvents: CorrelatedEvent[];
  remediationCommands: string[];
  confidence: number;
}

// --- Style Constant Mappings ---

export const SEVERITY_STYLES: Record<LogSeverity, { tagColor: string; isHighlighted: boolean }> = {
  INFO:  { tagColor: 'kd-text-primary-fixed-dim', isHighlighted: false },
  WARN:  { tagColor: 'kd-text-tertiary-fixed-dim', isHighlighted: false },
  ERROR: { tagColor: 'kd-text-error', isHighlighted: true },
  CRIT:  { tagColor: 'kd-text-error', isHighlighted: true },
};

export const STATUS_STYLES: Record<ActiveResource['status'], { dotColor: string; labelColor: string }> = {
  HEALTHY:  { dotColor: 'kd-bg-primary', labelColor: 'kd-text-primary' },
  CRITICAL: { dotColor: 'kd-bg-secondary', labelColor: 'kd-text-secondary' },
  WARNING:  { dotColor: 'kd-bg-tertiary', labelColor: 'kd-text-tertiary' },
};
