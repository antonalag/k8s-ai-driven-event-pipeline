/**
 * TypeScript interfaces for the 1-Click Remediation feature (Phase 17).
 */

/** Supported remediation action types */
export type SupportedAction = 'restart_deployment' | 'scale_deployment' | 'fix_container_image';

/** Request payload for POST /api/v1/remediations */
export interface RemediationRequest {
  correlationId: string;
  action: SupportedAction;
  deploymentName: string;
  namespace: string;
  replicas?: number;
  containerName?: string;
  correctImage?: string;
}

/** Successful response from POST /api/v1/remediations */
export interface RemediationResponse {
  correlationId: string;
  action: string;
  status: 'completed';
  timestamp: string;
  details: Record<string, unknown>;
}

/** RFC 7807 error response from failed remediation */
export interface RemediationError {
  type?: string;
  title: string;
  status: number;
  detail: string;
  correlationId?: string;
  action?: string;
  errorCode?: string;
}

/** Result of parsing an action text into a typed request */
export interface ParsedAction {
  action: SupportedAction;
  deploymentName: string;
  namespace?: string;
  replicas?: number;
  containerName?: string;
  correctImage?: string;
}
