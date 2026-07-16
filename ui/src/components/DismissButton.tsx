import { useState } from 'react';
import type { JSX } from 'react';
import { useDismissAnalysis } from '../hooks/useDismissAnalysis';

interface DismissButtonProps {
  analysisId: string;
}

export function DismissButton({ analysisId }: DismissButtonProps): JSX.Element {
  const { mutate, isPending, isSuccess, isError, error } = useDismissAnalysis();
  const [showReasonInput, setShowReasonInput] = useState(false);
  const [reason, setReason] = useState('');

  function handleDismiss() {
    if (showReasonInput) {
      mutate({ analysisId, reason: reason.trim() || undefined });
    } else {
      setShowReasonInput(true);
    }
  }

  function handleQuickDismiss() {
    mutate({ analysisId, reason: 'Dismissed by operator' });
  }

  function handleCancel() {
    setShowReasonInput(false);
    setReason('');
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter') {
      mutate({ analysisId, reason: reason.trim() || undefined });
    }
    if (e.key === 'Escape') {
      handleCancel();
    }
  }

  if (isSuccess) {
    return (
      <span
        className="kd-font-sans kd-text-code-sm kd-text-on-surface-variant kd-italic"
        data-testid="dismiss-success"
      >
        Dismissed
      </span>
    );
  }

  if (isError && error) {
    return (
      <span
        className="kd-font-sans kd-text-code-sm kd-text-secondary"
        data-testid="dismiss-error"
        title={error.detail}
      >
        Failed: {error.title}
      </span>
    );
  }

  if (showReasonInput) {
    return (
      <div className="kd-flex kd-items-center kd-gap-2" data-testid="dismiss-reason-input">
        <input
          type="text"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Reason (optional)"
          autoFocus
          className="kd-px-2 kd-py-1 kd-font-sans kd-text-code-sm kd-border kd-border-outline-variant kd-rounded kd-bg-surface kd-text-on-surface kd-w-48 focus:kd-border-primary focus:kd-outline-none kd-transition-colors"
          data-testid="dismiss-reason-field"
        />
        <button
          onClick={handleDismiss}
          disabled={isPending}
          className="kd-px-2 kd-py-1 kd-font-sans kd-text-code-sm kd-border kd-border-outline-variant kd-rounded kd-text-on-surface-variant hover:kd-border-secondary hover:kd-text-secondary kd-transition-colors kd-duration-200 disabled:kd-opacity-50"
          data-testid="dismiss-confirm-button"
        >
          {isPending ? 'Dismissing...' : 'Confirm'}
        </button>
        <button
          onClick={handleCancel}
          className="kd-px-2 kd-py-1 kd-font-sans kd-text-code-sm kd-text-on-surface-variant hover:kd-text-on-surface kd-transition-colors kd-duration-200"
          data-testid="dismiss-cancel-button"
        >
          Cancel
        </button>
      </div>
    );
  }

  return (
    <div className="kd-flex kd-items-center kd-gap-1">
      <button
        onClick={handleDismiss}
        disabled={isPending}
        aria-label="Dismiss analysis with reason"
        className="kd-px-2 kd-py-1 kd-font-sans kd-text-code-sm kd-border kd-border-outline-variant kd-rounded kd-text-on-surface-variant hover:kd-border-secondary hover:kd-text-secondary kd-transition-colors kd-duration-200 disabled:kd-opacity-50"
        data-testid="dismiss-button"
      >
        Dismiss
      </button>
      <button
        onClick={handleQuickDismiss}
        disabled={isPending}
        aria-label="Quick dismiss"
        title="Dismiss without reason"
        className="kd-px-1.5 kd-py-1 kd-font-sans kd-text-code-sm kd-text-on-surface-variant hover:kd-text-secondary kd-transition-colors kd-duration-200 disabled:kd-opacity-50"
        data-testid="dismiss-quick-button"
      >
        ✕
      </button>
    </div>
  );
}
