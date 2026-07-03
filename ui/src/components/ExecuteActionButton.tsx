import { useCallback } from 'react';
import { parseAction, getDisabledReason } from '../lib/action-parser';
import { useRemediation } from '../hooks/useRemediation';
import { RemediationBanner } from './RemediationBanner';
import type { RemediationRequest } from '../types/remediation';

interface ExecuteActionButtonProps {
  actionText: string;
  correlationId: string;
  namespace: string;
  onExecutionStart: () => void;
  onExecutionEnd: () => void;
  onSuccess?: () => void;
}

/**
 * Renders an "Execute" button for a recommended action.
 * Parses the action text to determine if automated execution is possible.
 * Shows loading spinner during execution, and success/error banners after.
 * Calls onSuccess after successful remediation (triggers card dismissal).
 */
export function ExecuteActionButton({
  actionText,
  correlationId,
  namespace,
  onExecutionStart,
  onExecutionEnd,
  onSuccess,
}: ExecuteActionButtonProps) {
  const { mutate, isPending, isSuccess, isError, data, error, reset } = useRemediation();

  const disabledReason = getDisabledReason(actionText, namespace);
  const isDisabled = disabledReason !== null;

  const handleExecute = useCallback(() => {
    const parsed = parseAction(actionText, namespace);
    if (!parsed) return;

    onExecutionStart();

    const request: RemediationRequest = {
      correlationId,
      action: parsed.action,
      deploymentName: parsed.deploymentName,
      namespace: parsed.namespace || namespace,
      replicas: parsed.replicas,
      containerName: parsed.containerName,
      correctImage: parsed.correctImage,
    };

    mutate(request, {
      onSuccess: () => {
        if (onSuccess) onSuccess();
      },
      onSettled: () => {
        onExecutionEnd();
      },
    });
  }, [actionText, correlationId, namespace, mutate, onExecutionStart, onExecutionEnd, onSuccess]);

  const handleRetry = useCallback(() => {
    reset();
    handleExecute();
  }, [reset, handleExecute]);

  return (
    <div data-testid="execute-action-wrapper">
      {!isSuccess && !isError && (
        <button
          onClick={handleExecute}
          disabled={isDisabled || isPending}
          title={disabledReason ?? undefined}
          aria-label={isDisabled ? disabledReason ?? 'Action not supported' : 'Execute action'}
          data-testid="execute-action-button"
          className={`kd-mt-1 kd-px-3 kd-py-1 kd-font-sans kd-text-code-sm kd-font-medium kd-rounded kd-transition-colors kd-duration-200 ${
            isDisabled
              ? 'kd-border kd-border-outline-variant kd-text-on-surface-variant kd-opacity-50 kd-cursor-not-allowed'
              : isPending
                ? 'kd-border kd-border-primary kd-text-primary kd-cursor-wait'
                : 'kd-bg-on-surface kd-text-surface hover:kd-bg-primary hover:kd-text-on-primary kd-cursor-pointer'
          }`}
        >
          {isPending ? (
            <span className="kd-flex kd-items-center kd-gap-1">
              <span className="kd-inline-block kd-w-3 kd-h-3 kd-border-2 kd-border-primary kd-border-t-transparent kd-rounded-full kd-animate-spin" />
              Executing...
            </span>
          ) : (
            'Execute'
          )}
        </button>
      )}

      {isSuccess && data && (
        <RemediationBanner variant="success" response={data} />
      )}

      {isError && error && (
        <RemediationBanner variant="error" error={error} onRetry={handleRetry} />
      )}
    </div>
  );
}
