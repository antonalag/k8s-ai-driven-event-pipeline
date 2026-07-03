/**
 * TypeScript interfaces mirroring the OpenAPI contract at
 * specs/openapi-ai-analyzer.v1.yaml.
 */

/** Mirrors components/schemas/AiAnalysisResponse */
export interface AiAnalysisResponse {
  podName: string;
  namespace: string;
  verdict: 'HEALTHY' | 'TRANSIENT_ISSUE' | 'CRITICAL_FAILURE' | 'DEGRADED';
  rootCauseAnalysis: string;
  recommendedActions: string[];
  analyzedAt: string;
}

/** Mirrors components/schemas/ProblemDetail (RFC 7807) */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}
