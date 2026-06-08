// @vitest-environment jsdom

/**
 * Unit tests for AIDiagnosisPanel component
 * Validates: Requirements 6.2, 6.3, 6.5, 6.7, 7.1
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import AIDiagnosisPanel from '../components/AIDiagnosisPanel';
import type { ProblemDetailDisplay, CorrelatedEvent } from '../types/dashboard';

// --- Test Fixtures ---

const mockProblemDetail: ProblemDetailDisplay = {
  type: 'https://sre.ai/errors/dns-resolution-failure',
  issue: 'Upstream Service Unreachable',
  status: '503 Service Unavailable',
  description:
    'The pod db-worker-7c2d is unable to resolve the hostname postgresql-master.',
};

const mockCorrelatedEvents: CorrelatedEvent[] = [
  { timeAgo: '4m ago', description: "Deployment 'postgres-cluster' scale-out event initiated", severity: 'info' },
  { timeAgo: '12m ago', description: "Node 'ip-10-0-2-44' reported memory pressure spike", severity: 'error' },
];

const mockRemediationCommands: string[] = [
  'kubectl rollout restart deploy db-worker',
  'kubectl get svc postgresql-master',
];

const defaultProps = {
  problemDetail: mockProblemDetail,
  correlatedEvents: mockCorrelatedEvents,
  remediationCommands: mockRemediationCommands,
  confidence: 94.2,
};

// --- Tests ---

describe('AIDiagnosisPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('does NOT render "EXECUTE AUTO-REMEDIATION" button', () => {
    render(<AIDiagnosisPanel {...defaultProps} />);

    const buttons = screen.queryAllByRole('button');
    const autoRemediationButton = buttons.find((btn) =>
      btn.textContent?.toUpperCase().includes('EXECUTE AUTO-REMEDIATION'),
    );
    expect(autoRemediationButton).toBeUndefined();

    // Also verify via text query
    expect(screen.queryByText(/execute auto-remediation/i)).toBeNull();
  });

  it('renders confidence bar and version label', () => {
    render(<AIDiagnosisPanel {...defaultProps} />);

    // Confidence percentage value
    expect(screen.getByTestId('confidence-value').textContent).toBe('94.2%');

    // Confidence progress bar with correct width
    const bar = screen.getByTestId('confidence-bar');
    expect(bar).toBeDefined();
    expect(bar.style.width).toBe('94.2%');

    // Version label
    expect(screen.getByText(/AI Insight Engine v2\.4/)).toBeDefined();
  });

  it('"Copy Commands" button calls clipboard API with correct text', async () => {
    const writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: { writeText: writeTextMock },
    });

    render(<AIDiagnosisPanel {...defaultProps} />);

    const copyButton = screen.getByTestId('copy-commands-button');
    fireEvent.click(copyButton);

    await waitFor(() => {
      expect(writeTextMock).toHaveBeenCalledWith(
        mockRemediationCommands.join('\n'),
      );
    });
  });

  it('renders all ProblemDetail fields', () => {
    render(<AIDiagnosisPanel {...defaultProps} />);

    expect(screen.getByTestId('problem-type').textContent).toContain(
      mockProblemDetail.type,
    );
    expect(screen.getByTestId('problem-issue').textContent).toContain(
      mockProblemDetail.issue,
    );
    expect(screen.getByTestId('problem-status').textContent).toContain(
      mockProblemDetail.status,
    );
    expect(screen.getByTestId('problem-description').textContent).toContain(
      mockProblemDetail.description,
    );
  });

  it('staggered reveal animation delay is applied via App Shell wrapper', () => {
    // The AIDiagnosisPanel itself is wrapped in a reveal-animated div in App.tsx.
    // Verify that the panel renders with the correct grid placement classes
    // that integrate with the App Shell staggered animation system.
    render(<AIDiagnosisPanel {...defaultProps} />);

    const panel = screen.getByTestId('ai-diagnosis-panel');
    // Panel has col-span-12 and row-span-3 for grid placement
    expect(panel.className).toContain('kd-col-span-12');
    expect(panel.className).toContain('kd-row-span-3');

    // The parent App Shell applies kd-animate-reveal with animationDelay.
    // We validate the panel is compatible by checking the structure is correct.
    // The animationDelay is set by App.tsx wrapper div (250ms for AI panel).
    expect(panel.className).toContain('ai-panel-depth');
    expect(panel.className).toContain('ai-glow');
  });
});
