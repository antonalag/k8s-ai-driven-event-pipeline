import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  fallbackMessage?: string;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('[ErrorBoundary]', error, errorInfo);
  }

  handleRetry = (): void => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div
          role="alert"
          className="kd-flex kd-flex-col kd-items-center kd-justify-center kd-gap-3 kd-py-12 kd-px-4"
        >
          <p className="kd-font-sans kd-text-body-md kd-text-on-surface-variant kd-text-center">
            {this.props.fallbackMessage ?? 'Something went wrong rendering this section.'}
          </p>
          {this.state.error && (
            <p className="kd-font-sans kd-text-body-sm kd-text-on-surface-variant kd-opacity-70 kd-text-center kd-max-w-md">
              {this.state.error.message}
            </p>
          )}
          <button
            onClick={this.handleRetry}
            className="kd-px-4 kd-py-2 kd-bg-on-surface kd-text-surface kd-rounded kd-font-sans kd-text-body-md kd-font-bold hover:kd-bg-primary hover:kd-text-on-primary kd-transition-colors kd-duration-200"
          >
            Retry
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
