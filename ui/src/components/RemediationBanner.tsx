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
 * Success: green with check icon, action name and timestamp.
 * Error: red with alert icon, RFC 7807 title + detail + retry button.
 */
export function RemediationBanner(props: RemediationBannerProps) {
  if (props.variant === 'success') {
    return (
      <div
        role="alert"
        aria-label="Remediation success"
        data-testid="remediation-banner-success"
        className="mt-2 flex items-start gap-2 p-3 rounded-md bg-green-50 border border-green-200 text-green-800 text-sm"
      >
        <span className="shrink-0" aria-hidden="true">✓</span>
        <span>
          Remediation applied — <strong>{props.response.action}</strong> completed at{' '}
          {new Date(props.response.timestamp).toLocaleTimeString()}
        </span>
      </div>
    );
  }

  return (
    <div
      role="alert"
      aria-label="Remediation error"
      data-testid="remediation-banner-error"
      className="mt-2 flex flex-col gap-2 p-3 rounded-md bg-red-50 border border-red-200 text-red-800 text-sm"
    >
      <div className="flex items-start gap-2">
        <span className="shrink-0" aria-hidden="true">✕</span>
        <div>
          <p className="font-semibold" data-testid="error-title">{props.error.title}</p>
          <p className="text-red-700" data-testid="error-detail">{props.error.detail}</p>
        </div>
      </div>
      <button
        onClick={props.onRetry}
        className="self-start px-3 py-1 text-xs font-medium bg-red-100 hover:bg-red-200 text-red-800 rounded"
        data-testid="retry-button"
      >
        Retry
      </button>
    </div>
  );
}
