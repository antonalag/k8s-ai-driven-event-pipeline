import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { EmptyState } from '../EmptyState';

afterEach(() => {
  cleanup();
});

describe('EmptyState', () => {
  it('renders with role="status" for accessibility', () => {
    render(<EmptyState />);
    const container = screen.getByRole('status');
    expect(container).toBeDefined();
  });

  it('displays message containing "analyses"', () => {
    render(<EmptyState />);
    const container = screen.getByRole('status');
    expect(container.textContent).toContain('analyses');
  });

  it('uses kd- prefixed CSS classes', () => {
    render(<EmptyState />);
    const container = screen.getByRole('status');
    expect(container.className).toMatch(/kd-/);
  });
});
