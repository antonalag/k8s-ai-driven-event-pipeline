/**
 * Unit tests for LogViewer component
 * Validates: Requirements 5.1, 5.3, 5.4, 5.6
 */
import { describe, it, expect } from 'vitest';
import { render, within } from '@testing-library/react';
import LogViewer from '../LogViewer';
import type { LogEntry } from '../../types/dashboard';

const sampleEntries: LogEntry[] = [
  { timestamp: '14:02:11.231', severity: 'INFO', message: 'Worker pool initialized with 4 threads.' },
  { timestamp: '14:02:15.982', severity: 'ERROR', message: 'ConnectionTimeoutException: Unable to establish socket.' },
  { timestamp: '14:02:21.110', severity: 'WARN', message: 'Retrying connection in 5000ms...' },
  { timestamp: '14:02:26.241', severity: 'CRIT', message: 'Liveness probe failed. Container marked unhealthy.' },
];

describe('LogViewer component', () => {
  describe('Requirement 5.1 - renders entries with timestamp, severity, message', () => {
    it('renders each log entry with timestamp, severity tag, and message text', () => {
      const { container } = render(<LogViewer entries={sampleEntries} />);

      const logLines = container.querySelectorAll('[data-testid="log-line"]');
      expect(logLines.length).toBe(sampleEntries.length);

      for (let i = 0; i < sampleEntries.length; i++) {
        const lineText = logLines[i].textContent ?? '';
        expect(lineText).toContain(sampleEntries[i].timestamp);
        expect(lineText).toContain(sampleEntries[i].severity);
        expect(lineText).toContain(sampleEntries[i].message);
      }
    });
  });

  describe('Requirement 5.4 - log content container styling', () => {
    it('applies correct classes to the log content container', () => {
      const { container } = render(<LogViewer entries={sampleEntries} />);

      const logContent = container.querySelector('[data-testid="log-content"]');
      expect(logContent).not.toBeNull();
      expect(logContent!.className).toContain('kd-flex-1');
      expect(logContent!.className).toContain('kd-overflow-auto');
      expect(logContent!.className).toContain('kd-font-mono');
      expect(logContent!.className).toContain('kd-bg-surface');
    });
  });

  describe('Requirement 5.3 - ERROR/CRIT lines have highlight border and tinted background', () => {
    it('ERROR lines have border-l-2, border-secondary, and bg-secondary/5 classes', () => {
      const errorEntry: LogEntry[] = [
        { timestamp: '14:02:15.982', severity: 'ERROR', message: 'Connection error' },
      ];
      const { container } = render(<LogViewer entries={errorEntry} />);

      const logLine = container.querySelector('[data-testid="log-line"]');
      expect(logLine).not.toBeNull();
      const classes = logLine!.className;
      expect(classes).toContain('kd-border-l-2');
      expect(classes).toContain('kd-border-secondary');
      expect(classes).toContain('kd-bg-secondary/5');
    });

    it('CRIT lines have border-l-2, border-secondary, and bg-secondary/5 classes', () => {
      const critEntry: LogEntry[] = [
        { timestamp: '14:02:26.241', severity: 'CRIT', message: 'Container unhealthy' },
      ];
      const { container } = render(<LogViewer entries={critEntry} />);

      const logLine = container.querySelector('[data-testid="log-line"]');
      expect(logLine).not.toBeNull();
      const classes = logLine!.className;
      expect(classes).toContain('kd-border-l-2');
      expect(classes).toContain('kd-border-secondary');
      expect(classes).toContain('kd-bg-secondary/5');
    });

    it('INFO lines do NOT have highlight classes', () => {
      const infoEntry: LogEntry[] = [
        { timestamp: '14:02:11.231', severity: 'INFO', message: 'All good' },
      ];
      const { container } = render(<LogViewer entries={infoEntry} />);

      const logLine = container.querySelector('[data-testid="log-line"]');
      expect(logLine).not.toBeNull();
      const classes = logLine!.className;
      expect(classes).not.toContain('kd-border-l-2');
      expect(classes).not.toContain('kd-border-secondary');
      expect(classes).not.toContain('kd-bg-secondary/5');
    });
  });

  describe('Requirement 5.6 - AUTOSCROLL and CLEAR buttons exist', () => {
    it('renders an AUTOSCROLL button', () => {
      const { container } = render(<LogViewer entries={sampleEntries} />);
      const panel = within(container.querySelector('[data-testid="log-viewer-panel"]')!);
      const btn = panel.getByRole('button', { name: /AUTOSCROLL/i });
      expect(btn).toBeDefined();
    });

    it('renders a CLEAR button', () => {
      const { container } = render(<LogViewer entries={sampleEntries} />);
      const panel = within(container.querySelector('[data-testid="log-viewer-panel"]')!);
      const btn = panel.getByRole('button', { name: /CLEAR/i });
      expect(btn).toBeDefined();
    });
  });
});
