import { useCallback } from 'react';
import { parseAction } from '../lib/action-parser';
import { useRemediation } from '../hooks/useRemediation';
import { RemediationBanner } from './RemediationBanner';
import type { RemediationRequest } from '../types/remediation';

interface ExecuteActionButtonProps {
  actionText: string;
  correlationId: string;
  namespace: string;
  onExecutionStart: () => void;
  onExecutionEnd: () => void;
}

export function ExecuteActionButton({
  actionText,
  correlationId,
  namespace,
  onExecutionStart,
  onExecutionEnd,
}: ExecuteActionButtonProps) {
  const { mutate, isPending, isSuccess, isError, data, error, reset } = useRemediation();

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
      onSettled: () => {
        onExecutionEnd();
      },
    });
  }, [actionText, correlationId, namespace, mutate, onExecutionStart, onExecutionEnd]);

  const handleRetry = useCallback(() => {
    reset();
    handleExecute();
  }, [reset, handleExecute]);

  return (
    <div data-testid="execute-action-wrapper">
      {!isSuccess && !isError && (
        <button
          onClick={handleExecute}
          disabled={isPending}
          aria-label="Execute action"
          data-testid="execute-action-button"
          className={`kd-px-3 kd-py-1 kd-font-sans kd-text-code-sm kd-font-medium kd-rounded kd-transition-colors kd-duration-200 ${
            isPending
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
