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

/** Status of resolved analyses in the audit log */
export type ResolvedStatus = 'REMEDIATED' | 'DISMISSED';

/** Single entry in the audit log history response */
export interface HistoryResponseDto {
  resolvedAt: string;       // ISO-8601 timestamp
  podName: string;
  namespace: string;
  verdict: string;
  status: ResolvedStatus;
  rootCauseAnalysis: string;
  recommendedActions: string[];
  modelUsed: string;
}

/** Paginated response from GET /api/v1/analyses/history */
export interface AuditLogHistoryResponse {
  content: HistoryResponseDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
