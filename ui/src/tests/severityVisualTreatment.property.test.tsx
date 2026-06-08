/**
 * Feature: ui-stitch-visual-integration, Property 3: Severity-driven visual treatment
 *
 * **Validates: Requirements 5.2, 5.3**
 *
 * For any LogEntry, the rendered severity tag has the color class corresponding to its
 * severity level (primary-fixed-dim for INFO, tertiary-fixed-dim for WARN, error for ERROR/CRIT),
 * AND the line has highlight styling (left border + tinted background) if and only if the
 * severity is ERROR or CRIT.
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { render } from '@testing-library/react';
import LogViewer from '../components/LogViewer';
import type { LogEntry, LogSeverity } from '../types/dashboard';
import { SEVERITY_STYLES } from '../types/dashboard';

// --- Generators ---

const severityArb = fc.constantFrom('INFO', 'WARN', 'ERROR', 'CRIT' as const);

const logEntryArb = fc.record({
  timestamp: fc.stringMatching(/^\d{2}:\d{2}:\d{2}\.\d{3}$/),
  severity: severityArb,
  message: fc.string({ minLength: 1, maxLength: 500 }),
  isStackTrace: fc.boolean(),
});

// --- Property Test ---

describe('Property 3: Severity-driven visual treatment', () => {
  it('for any LogEntry, severity tag has correct color class AND highlight styling iff severity is ERROR/CRIT', () => {
    fc.assert(
      fc.property(logEntryArb, (entry: { timestamp: string; severity: LogSeverity; message: string; isStackTrace: boolean }) => {
        const logEntry: LogEntry = {
          timestamp: entry.timestamp,
          severity: entry.severity,
          message: entry.message,
          isStackTrace: entry.isStackTrace,
        };

        const { container } = render(<LogViewer entries={[logEntry]} />);

        const logLine = container.querySelector('[data-testid="log-line"]');
        expect(logLine).not.toBeNull();

        const expectedStyles = SEVERITY_STYLES[entry.severity];

        // --- Verify severity tag color class ---
        // Severity tag is only rendered when isStackTrace is false
        if (!entry.isStackTrace) {
          const severityTag = logLine!.querySelector('[data-testid="severity-tag"]');
          expect(severityTag).not.toBeNull();
          expect(severityTag!.className).toContain(expectedStyles.tagColor);
        }

        // --- Verify highlight styling (border + background) iff ERROR/CRIT ---
        const lineClasses = logLine!.className;

        if (expectedStyles.isHighlighted) {
          // ERROR and CRIT lines must have highlight classes
          expect(lineClasses).toContain('kd-border-error');
          expect(lineClasses).toContain('kd-bg-error/5');
          expect(lineClasses).toContain('kd-border-l-4');
        } else {
          // INFO and WARN lines must NOT have highlight classes
          expect(lineClasses).not.toContain('kd-border-error');
          expect(lineClasses).not.toContain('kd-bg-error/5');
        }
      }),
      { numRuns: 100 },
    );
  });
});
