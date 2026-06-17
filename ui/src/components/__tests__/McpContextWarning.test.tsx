import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { McpContextWarning } from '../McpContextWarning';

afterEach(() => {
  cleanup();
});

describe('McpContextWarning', () => {
  it('renders warning when mcpContextAvailable is false', () => {
    render(<McpContextWarning mcpContextAvailable={false} />);
    const alert = screen.getByRole('alert');
    expect(alert).toBeDefined();
    expect(alert.textContent).toContain('without live cluster context');
  });

  it('renders nothing when mcpContextAvailable is true', () => {
    const { container } = render(<McpContextWarning mcpContextAvailable={true} />);
    expect(container.innerHTML).toBe('');
  });

  it('includes data-testid for querying', () => {
    render(<McpContextWarning mcpContextAvailable={false} />);
    const element = screen.getByTestId('mcp-context-warning');
    expect(element).toBeDefined();
  });

  it('mentions MCP circuit breaker in the warning text', () => {
    render(<McpContextWarning mcpContextAvailable={false} />);
    const alert = screen.getByRole('alert');
    expect(alert.textContent).toContain('MCP circuit breaker');
  });
});
