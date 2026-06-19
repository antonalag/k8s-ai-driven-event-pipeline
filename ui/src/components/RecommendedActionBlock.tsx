import { useState, useCallback } from 'react';
import { ExecuteActionButton } from './ExecuteActionButton';

interface RecommendedActionBlockProps {
  actions: string[];
  correlationId?: string;
  namespace?: string;
}

export function RecommendedActionBlock({ actions, correlationId, namespace }: RecommendedActionBlockProps) {
  if (!actions || actions.length === 0) return null;

  return (
    <div className="space-y-2" data-testid="recommended-actions">
      {actions.map((action, index) => (
        <ActionItem
          key={index}
          action={action}
          index={index}
          correlationId={correlationId}
          namespace={namespace}
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
}: {
  action: string;
  index: number;
  correlationId?: string;
  namespace?: string;
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
    <div className="relative group" data-testid={`action-block-${index}`}>
      <pre className="bg-gray-900 text-green-400 text-sm font-mono p-3 rounded-md overflow-x-auto whitespace-pre-wrap break-all">
        <code>{action}</code>
      </pre>
      <div className="flex items-center gap-2 mt-1">
        <button
          onClick={handleCopy}
          className="px-2 py-1 text-xs bg-gray-700 hover:bg-gray-600 text-gray-300 rounded"
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
          />
        )}
      </div>

      {isExecuting && (
        <div className="absolute inset-0 bg-gray-900/20 rounded-md pointer-events-none" />
      )}
    </div>
  );
}
