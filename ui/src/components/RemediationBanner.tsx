import type { RemediationResponse, RemediationError } from '../types/remediation';

interface SuccessBannerProps {
  variant: 'success';
  response: RemediationResponse;
}

interface ErrorBannerProps {
  variant: 'error';
  error: RemediationError;
  onRetry: () => void;
}

type RemediationBannerProps = SuccessBannerProps | ErrorBannerProps;

/**
 * Displays a success or error banner after a remediation action execution.
 * Success: Emerald border with check, action name and timestamp.
 * Error: Rose border with alert, RFC 7807 title + detail + retry button.
 */
export function RemediationBanner(props: RemediationBannerProps) {
  if (props.variant === 'success') {
    return (
      <div
        role="alert"
        aria-label="Remediation success"
        data-testid="remediation-banner-success"
        className="kd-mt-2 kd-flex kd-items-start kd-gap-2 kd-p-3 kd-rounded kd-border kd-border-primary kd-bg-primary/5 kd-font-sans kd-text-code-sm kd-text-primary"
      >
        <span className="kd-shrink-0" aria-hidden="true">✓</span>
        <span>
          Remediation applied — <strong>{props.response.action}</strong> completed at{' '}
          <span className="kd-font-mono">{new Date(props.response.timestamp).toLocaleTimeString()}</span>
        </span>
      </div>
    );
  }

  return (
    <div
      role="alert"
      aria-label="Remediation error"
      data-testid="remediation-banner-error"
      className="kd-mt-2 kd-flex kd-flex-col kd-gap-2 kd-p-3 kd-rounded kd-border kd-border-secondary kd-bg-secondary/5 kd-font-sans kd-text-code-sm"
    >
      <div className="kd-flex kd-items-start kd-gap-2">
        <span className="kd-shrink-0 kd-text-secondary" aria-hidden="true">✕</span>
        <div>
          <p className="kd-font-bold kd-text-secondary" data-testid="error-title">{props.error.title}</p>
          <p className="kd-text-on-surface-variant" data-testid="error-detail">{props.error.detail}</p>
        </div>
      </div>
      <button
        onClick={props.onRetry}
        className="kd-self-start kd-px-3 kd-py-1 kd-font-sans kd-text-code-sm kd-font-medium kd-border kd-border-secondary kd-text-secondary kd-rounded hover:kd-bg-secondary/10 kd-transition-colors kd-duration-200"
        data-testid="retry-button"
      >
        Retry
      </button>
    </div>
  );
}
