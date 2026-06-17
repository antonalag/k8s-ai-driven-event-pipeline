import { useState } from 'react';

interface RecommendedActionBlockProps {
  actions: string[];
}

export function RecommendedActionBlock({ actions }: RecommendedActionBlockProps) {
  if (!actions || actions.length === 0) return null;

  return (
    <div className="space-y-2" data-testid="recommended-actions">
      {actions.map((action, index) => (
        <ActionItem key={index} action={action} index={index} />
      ))}
    </div>
  );
}

function ActionItem({ action, index }: { action: string; index: number }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(action);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API may not be available in all contexts
    }
  };

  return (
    <div className="relative group" data-testid={`action-block-${index}`}>
      <pre className="bg-gray-900 text-green-400 text-sm font-mono p-3 rounded-md overflow-x-auto whitespace-pre-wrap break-all">
        <code>{action}</code>
      </pre>
      <button
        onClick={handleCopy}
        className="absolute top-2 right-2 px-2 py-1 text-xs bg-gray-700 hover:bg-gray-600 text-gray-300 rounded opacity-0 group-hover:opacity-100 transition-opacity"
        aria-label={`Copy action ${index + 1}`}
        data-testid={`copy-button-${index}`}
      >
        {copied ? '✓ Copied' : 'Copy'}
      </button>
    </div>
  );
}
