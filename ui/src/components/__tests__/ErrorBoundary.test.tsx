import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import type { JSX } from 'react';
import { ErrorBoundary } from '../ErrorBoundary';

afterEach(() => {
  cleanup();
});

function ThrowingChild({ shouldThrow }: { shouldThrow: boolean }): JSX.Element {
  if (shouldThrow) {
    throw new Error('Test render error');
  }
  return <p>Child rendered</p>;
}

describe('ErrorBoundary', () => {
  it('renders children when no error occurs', () => {
    render(
      <ErrorBoundary>
        <p>Hello</p>
      </ErrorBoundary>,
    );
    expect(screen.getByText('Hello')).toBeDefined();
  });

  it('renders fallback UI with role="alert" when child throws', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    );
    const alert = screen.getByRole('alert');
    expect(alert).toBeDefined();
    consoleSpy.mockRestore();
  });

  it('shows default fallback message when no prop provided', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Something went wrong rendering this section.')).toBeDefined();
    consoleSpy.mockRestore();
  });

  it('shows custom fallback message when prop is provided', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(
      <ErrorBoundary fallbackMessage="Custom error message">
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Custom error message')).toBeDefined();
    consoleSpy.mockRestore();
  });

  it('displays the error message text', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Test render error')).toBeDefined();
    consoleSpy.mockRestore();
  });

  it('renders a Retry button that resets error state', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const { rerender } = render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toBeDefined();

    // Re-render with non-throwing child, then click retry
    rerender(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={false} />
      </ErrorBoundary>,
    );

    fireEvent.click(screen.getByText('Retry'));
    expect(screen.getByText('Child rendered')).toBeDefined();
    expect(screen.queryByRole('alert')).toBeNull();
    consoleSpy.mockRestore();
  });

  it('uses kd- prefixed CSS classes in fallback UI', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    );
    const alert = screen.getByRole('alert');
    expect(alert.className).toMatch(/kd-/);
    consoleSpy.mockRestore();
  });
});
