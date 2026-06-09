import type { AiAnalysisResponse, ProblemDetail } from '../types/api';
import type { AIDiagnosisPanelProps, CorrelatedEvent, ProblemDetailDisplay } from '../types/dashboard';

/**
 * Deterministic mapping from backend verdict enum to frontend severity.
 */
export const VERDICT_SEVERITY_MAP: Record<AiAnalysisResponse['verdict'], CorrelatedEvent['severity']> = {
  CRITICAL_FAILURE: 'error',
  TRANSIENT_ISSUE: 'warning',
  HEALTHY: 'info',
};

/**
 * Deterministic mapping from backend verdict enum to human-readable display string.
 */
export const VERDICT_DISPLAY_MAP: Record<AiAnalysisResponse['verdict'], string> = {
  CRITICAL_FAILURE: 'Critical Failure',
  TRANSIENT_ISSUE: 'Transient Issue',
  HEALTHY: 'Healthy',
};

/**
 * Extracts the first sentence from rootCauseAnalysis, truncated at 120 characters.
 * Splits on '. ' or '.' to find the first sentence boundary.
 */
export function extractIssueSummary(rootCauseAnalysis: string): string {
  const periodSpaceIndex = rootCauseAnalysis.indexOf('. ');
  const periodIndex = rootCauseAnalysis.indexOf('.');

  let sentence: string;

  if (periodSpaceIndex !== -1) {
    sentence = rootCauseAnalysis.slice(0, periodSpaceIndex + 1);
  } else if (periodIndex !== -1) {
    sentence = rootCauseAnalysis.slice(0, periodIndex + 1);
  } else {
    sentence = rootCauseAnalysis;
  }

  if (sentence.length > 120) {
    return sentence.slice(0, 120);
  }

  return sentence;
}

/**
 * Formats an ISO 8601 timestamp to a locale-formatted date-time string.
 * Uses Intl.DateTimeFormat with dateStyle: 'medium', timeStyle: 'short'.
 */
export function formatAnalyzedAt(isoTimestamp: string): string {
  const date = new Date(isoTimestamp);
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

/**
 * Maps a single AiAnalysisResponse to AIDiagnosisPanelProps.
 * Exhaustively destructures all fields for compile-time safety.
 * Pure function — no side effects.
 */
export function mapAnalysisToProps(response: AiAnalysisResponse): AIDiagnosisPanelProps {
  const { podName, namespace, verdict, rootCauseAnalysis, recommendedActions, analyzedAt } = response;

  // Suppress unused variable warnings — these are destructured for compile-time exhaustiveness
  void podName;
  void namespace;
  void analyzedAt;

  return {
    problemDetail: {
      status: VERDICT_DISPLAY_MAP[verdict],
      description: rootCauseAnalysis,
      issue: extractIssueSummary(rootCauseAnalysis),
      type: `urn:k8s:verdict:${verdict.toLowerCase()}`,
    },
    remediationCommands: recommendedActions,
    confidence: 1.0,
    correlatedEvents: [],
  };
}

/**
 * Maps an array of AiAnalysisResponse to CorrelatedEvent[].
 * Each response is mapped to a single correlated event with relative time.
 */
export function mapToCorrelatedEvents(responses: AiAnalysisResponse[]): CorrelatedEvent[] {
  return responses.map((response) => {
    const { verdict, rootCauseAnalysis, analyzedAt } = response;

    return {
      timeAgo: computeRelativeTime(analyzedAt),
      description: rootCauseAnalysis,
      severity: VERDICT_SEVERITY_MAP[verdict],
    };
  });
}

/**
 * Computes a relative time string from an ISO timestamp compared to now.
 * Formats as "Xm ago", "Xh ago", "Xd ago".
 */
function computeRelativeTime(isoTimestamp: string): string {
  const now = Date.now();
  const then = new Date(isoTimestamp).getTime();
  const diffMs = now - then;

  const diffSeconds = Math.floor(diffMs / 1000);
  const diffMinutes = Math.floor(diffSeconds / 60);
  const diffHours = Math.floor(diffMinutes / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffDays > 0) {
    return `${diffDays}d ago`;
  }
  if (diffHours > 0) {
    return `${diffHours}h ago`;
  }
  if (diffMinutes > 0) {
    return `${diffMinutes}m ago`;
  }
  return `${diffSeconds}s ago`;
}

/**
 * Maps ProblemDetail (RFC 7807) to ProblemDetailDisplay with fallback strings.
 * Provides safe defaults for all undefined fields.
 */
export function mapProblemDetailToDisplay(problem: ProblemDetail): ProblemDetailDisplay {
  return {
    type: problem.type ?? '—',
    issue: problem.title ?? 'API Error',
    status: problem.status !== undefined ? String(problem.status) : '—',
    description: problem.detail ?? 'An unexpected error occurred. Please retry or contact your platform team.',
  };
}
