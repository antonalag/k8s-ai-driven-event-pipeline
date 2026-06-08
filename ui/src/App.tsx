import { useState } from 'react';
import type { JSX } from 'react';
import Sidebar from './components/Sidebar';
import TopBar from './components/TopBar';
import LogViewer from './components/LogViewer';
import AIDiagnosisPanel from './components/AIDiagnosisPanel';
import type {
  NavItemId,
  LogEntry,
  ProblemDetailDisplay,
  CorrelatedEvent,
} from './types/dashboard';

// --- Static Mock Data (matching docs/ui-code.html reference) ---

const BREADCRUMBS: { label: string; isActive?: boolean }[] = [
  { label: 'cluster-01' },
  { label: 'default' },
  { label: 'ai-analyzer', isActive: true },
];

const LOG_ENTRIES: LogEntry[] = [
  { timestamp: '14:02:11.231', severity: 'INFO', message: 'Worker pool initialized with 4 threads. Threads healthy and responsive.' },
  { timestamp: '14:02:12.455', severity: 'INFO', message: 'Attempting connection to postgresql-master.svc.cluster.local:5432' },
  { timestamp: '14:02:15.982', severity: 'ERROR', message: 'ConnectionTimeoutException: Unable to establish socket to host.' },
  { timestamp: '14:02:16.001', severity: 'ERROR', message: 'java.net.ConnectException: Connection refused (Connection refused)', isStackTrace: false },
  { timestamp: '14:02:21.110', severity: 'WARN', message: 'Retrying connection in 5000ms... (Attempt 3/5)' },
  { timestamp: '14:02:26.241', severity: 'CRIT', message: 'Liveness probe failed. Container marked as unhealthy by kube-scheduler.' },
];

const PROBLEM_DETAIL: ProblemDetailDisplay = {
  type: 'https://sre.ai/errors/dns-resolution-failure',
  issue: 'Upstream Service Unreachable',
  status: '503 Service Unavailable',
  description:
    'The pod db-worker-7c2d is unable to resolve the hostname postgresql-master. Analysis suggests a potential CoreDNS synchronization lag following the recent deployment of postgres-cluster.',
};

const CORRELATED_EVENTS: CorrelatedEvent[] = [
  { timeAgo: '4m ago', description: "Deployment 'postgres-cluster' scale-out event initiated", severity: 'info' },
  { timeAgo: '12m ago', description: "Node 'ip-10-0-2-44' reported memory pressure spike", severity: 'error' },
];

const REMEDIATION_COMMANDS: string[] = [
  'kubectl rollout restart deploy db-worker',
  'kubectl get svc postgresql-master',
];

const AI_CONFIDENCE = 94.2;

// --- App Shell ---

function App(): JSX.Element {
  const [activeNavItem, setActiveNavItem] = useState<NavItemId>('dashboard');

  return (
    <div className="kd-flex kd-overflow-hidden kd-font-body-md kd-text-body-md">
      {/* Fixed Sidebar (left) */}
      <Sidebar activeNavItem={activeNavItem} onNavItemClick={setActiveNavItem} />

      {/* Fluid Main Area (right) */}
      <main className="kd-ml-72 kd-flex-1 kd-flex kd-flex-col kd-h-screen kd-overflow-hidden">
        {/* Sticky TopBar */}
        <TopBar breadcrumbs={BREADCRUMBS} />

        {/* CSS Grid Content Area: 12 columns × 6 rows */}
        <div className="kd-flex-1 kd-p-6 kd-grid kd-grid-cols-12 kd-grid-rows-6 kd-gap-6 kd-overflow-hidden">
          {/* LogViewer - col-span-12, row-span-3, staggered reveal delay 100ms */}
          <div
            className="kd-col-span-12 kd-row-span-3 kd-animate-reveal"
            style={{ animationDelay: '100ms', animationFillMode: 'backwards' }}
          >
            <LogViewer entries={LOG_ENTRIES} />
          </div>

          {/* AIDiagnosisPanel - col-span-12, row-span-3, staggered reveal delay 250ms */}
          <div
            className="kd-col-span-12 kd-row-span-3 kd-animate-reveal"
            style={{ animationDelay: '250ms', animationFillMode: 'backwards' }}
          >
            <AIDiagnosisPanel
              problemDetail={PROBLEM_DETAIL}
              correlatedEvents={CORRELATED_EVENTS}
              remediationCommands={REMEDIATION_COMMANDS}
              confidence={AI_CONFIDENCE}
            />
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;
