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
}

/**
 * Renders an "Execute" button for a recommended action.
 * Parses the action text to determine if automated execution is possible.
 * Shows loading spinner during execution, and success/error banners after.
 * Disables with tooltip when action text cannot be parsed.
 */
export function ExecuteActionButton({
  actionText,
  correlationId,
  namespace,
  onExecutionStart,
  onExecutionEnd,
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
      {/* Execute button */}
      {!isSuccess && !isError && (
        <button
          onClick={handleExecute}
          disabled={isDisabled || isPending}
          title={disabledReason ?? undefined}
          aria-label={isDisabled ? disabledReason ?? 'Action not supported' : 'Execute action'}
          data-testid="execute-action-button"
          className={`
            mt-1 px-3 py-1 text-xs font-medium rounded transition-colors
            ${isDisabled
              ? 'bg-gray-200 text-gray-400 cursor-not-allowed'
              : isPending
                ? 'bg-blue-100 text-blue-600 cursor-wait'
                : 'bg-blue-600 hover:bg-blue-700 text-white cursor-pointer'
            }
          `}
        >
          {isPending ? (
            <span className="flex items-center gap-1">
              <span className="inline-block w-3 h-3 border-2 border-blue-600 border-t-transparent rounded-full animate-spin" />
              Executing...
            </span>
          ) : (
            'Execute'
          )}
        </button>
      )}

      {/* Success banner */}
      {isSuccess && data && (
        <RemediationBanner variant="success" response={data} />
      )}

      {/* Error banner */}
      {isError && error && (
        <RemediationBanner variant="error" error={error} onRetry={handleRetry} />
      )}
    </div>
  );
}
