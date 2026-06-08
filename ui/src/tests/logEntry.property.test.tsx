/**
 * Feature: ui-stitch-visual-integration, Property 2: Log entry structural completeness
 *
 * **Validates: Requirements 5.1**
 *
 * For any valid LogEntry, rendered output contains timestamp, severity tag, and message text.
 * When isStackTrace is false, all three elements (timestamp, severity tag, message) are present.
 * When isStackTrace is true, timestamp and message are always present (severity tag is omitted
 * as stack traces are continuation lines).
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { render } from '@testing-library/react';
import LogViewer from '../components/LogViewer';
import type { LogEntry, LogSeverity } from '../types/dashboard';

// --- Generators ---

const severityArb = fc.constantFrom('INFO', 'WARN', 'ERROR', 'CRIT' as const);

const logEntryArb = fc.record({
  timestamp: fc.stringMatching(/^\d{2}:\d{2}:\d{2}\.\d{3}$/),
  severity: severityArb,
  message: fc.string({ minLength: 1, maxLength: 500 }),
  isStackTrace: fc.boolean(),
});

// --- Property Test ---

describe('Property 2: Log entry structural completeness', () => {
  it('for any valid LogEntry, rendered output contains timestamp and message text; severity tag is present when isStackTrace is false', () => {
    fc.assert(
      fc.property(logEntryArb, (entry: { timestamp: string; severity: LogSeverity; message: string; isStackTrace: boolean }) => {
        const logEntry: LogEntry = {
          timestamp: entry.timestamp,
          severity: entry.severity,
          message: entry.message,
          isStackTrace: entry.isStackTrace,
        };

        const { container } = render(<LogViewer entries={[logEntry]} />);

        const logLines = container.querySelectorAll('[data-testid="log-line"]');
        expect(logLines.length).toBe(1);

        const lineElement = logLines[0];
        const lineText = lineElement.textContent ?? '';

        // Timestamp must always be present
        expect(lineText).toContain(entry.timestamp);

        // Message must always be present
        expect(lineText).toContain(entry.message);

        // Severity tag is present when not a stack trace line
        if (!entry.isStackTrace) {
          const severityTag = lineElement.querySelector('[data-testid="severity-tag"]');
          expect(severityTag).not.toBeNull();
          expect(severityTag!.textContent).toContain(entry.severity);
        }
      }),
      { numRuns: 100 },
    );
  });
});
