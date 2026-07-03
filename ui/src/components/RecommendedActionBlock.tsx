import { useState, useCallback } from 'react';
import { ExecuteActionButton } from './ExecuteActionButton';

interface RecommendedActionBlockProps {
  actions: string[];
  correlationId?: string;
  namespace?: string;
  onRemediationSuccess?: () => void;
}

export function RecommendedActionBlock({ actions, correlationId, namespace, onRemediationSuccess }: RecommendedActionBlockProps) {
  if (!actions || actions.length === 0) return null;

  return (
    <div className="kd-space-y-2" data-testid="recommended-actions">
      {actions.map((action, index) => (
        <ActionItem
          key={index}
          action={action}
          index={index}
          correlationId={correlationId}
          namespace={namespace}
          onRemediationSuccess={onRemediationSuccess}
        />
      ))}
    </div>
  );
}

function ActionItem({
  action,
  index,
  correlationId,
  namespace,
  onRemediationSuccess,
}: {
  action: string;
  index: number;
  correlationId?: string;
  namespace?: string;
  onRemediationSuccess?: () => void;
}) {
  const [copied, setCopied] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(action);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API may not be available in all contexts
    }
  };

  const handleExecutionStart = useCallback(() => setIsExecuting(true), []);
  const handleExecutionEnd = useCallback(() => setIsExecuting(false), []);

  return (
    <div className="kd-relative" data-testid={`action-block-${index}`}>
      {/* Code block */}
      <pre className="kd-bg-surface kd-border kd-border-outline-variant kd-rounded kd-p-3 kd-font-mono kd-text-code-sm kd-text-primary kd-overflow-x-auto kd-whitespace-pre-wrap kd-break-all">
        <code>{action}</code>
      </pre>

      {/* Action buttons row */}
      <div className="kd-flex kd-items-center kd-gap-2 kd-mt-1">
        <button
          onClick={handleCopy}
          className="kd-px-2 kd-py-1 kd-font-sans kd-text-code-sm kd-border kd-border-outline-variant kd-rounded kd-text-on-surface-variant hover:kd-border-primary hover:kd-text-primary kd-transition-colors kd-duration-200"
          aria-label={`Copy action ${index + 1}`}
          data-testid={`copy-button-${index}`}
        >
          {copied ? '✓ Copied' : 'Copy'}
        </button>

        {correlationId && namespace && (
          <ExecuteActionButton
            actionText={action}
            correlationId={correlationId}
            namespace={namespace}
            onExecutionStart={handleExecutionStart}
            onExecutionEnd={handleExecutionEnd}
            onSuccess={onRemediationSuccess}
          />
        )}
      </div>

      {/* Execution overlay */}
      {isExecuting && (
        <div className="kd-absolute kd-inset-0 kd-bg-surface/30 kd-rounded kd-pointer-events-none" />
      )}
    </div>
  );
}
