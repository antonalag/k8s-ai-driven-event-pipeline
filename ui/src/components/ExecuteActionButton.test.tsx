/**
 * Tests for ExecuteActionButton component.
 * Validates: Requirements 11.1-11.7 (button states, transitions, disabled logic)
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { ExecuteActionButton } from './ExecuteActionButton';
import { createQueryWrapper } from '../test-utils/queryWrapper';

afterEach(() => {
  cleanup();
});

describe('ExecuteActionButton', () => {
  const defaultProps = {
    correlationId: '550e8400-e29b-41d4-a716-446655440000',
    namespace: 'chaos-validation',
    onExecutionStart: vi.fn(),
    onExecutionEnd: vi.fn(),
  };

  describe('disabled state', () => {
    it('disables button for unparseable action text', () => {
      const Wrapper = createQueryWrapper();
      const { container } = render(
        <Wrapper>
          <ExecuteActionButton
            {...defaultProps}
            actionText="Check pod logs for details"
          />
        </Wrapper>,
      );

      const button = container.querySelector('[data-testid="execute-action-button"]') as HTMLButtonElement;
      expect(button).not.toBeNull();
      expect(button.disabled).toBe(true);
      expect(button.getAttribute('title')).toBe('Cannot auto-parse this action');
    });

    it('disables button for scale with replicas > 10', () => {
      const Wrapper = createQueryWrapper();
      const { container } = render(
        <Wrapper>
          <ExecuteActionButton
            {...defaultProps}
            actionText="kubectl scale deployment/app --replicas=15 -n default"
          />
        </Wrapper>,
      );

      const button = container.querySelector('[data-testid="execute-action-button"]') as HTMLButtonElement;
      expect(button).not.toBeNull();
      expect(button.disabled).toBe(true);
    });
  });

  describe('enabled state', () => {
    it('enables button for parseable restart command', () => {
      const Wrapper = createQueryWrapper();
      const { container } = render(
        <Wrapper>
          <ExecuteActionButton
            {...defaultProps}
            actionText="kubectl rollout restart deployment/my-app -n chaos-validation"
          />
        </Wrapper>,
      );

      const button = container.querySelector('[data-testid="execute-action-button"]') as HTMLButtonElement;
      expect(button).not.toBeNull();
      expect(button.disabled).toBe(false);
      expect(button.textContent).toBe('Execute');
    });

    it('enables button for parseable scale command', () => {
      const Wrapper = createQueryWrapper();
      const { container } = render(
        <Wrapper>
          <ExecuteActionButton
            {...defaultProps}
            actionText="kubectl scale deployment/web --replicas=3 -n staging"
          />
        </Wrapper>,
      );

      const button = container.querySelector('[data-testid="execute-action-button"]') as HTMLButtonElement;
      expect(button).not.toBeNull();
      expect(button.disabled).toBe(false);
    });

    it('enables button for parseable set image command', () => {
      const Wrapper = createQueryWrapper();
      const { container } = render(
        <Wrapper>
          <ExecuteActionButton
            {...defaultProps}
            actionText="kubectl set image deployment/api main=nginx:1.27 -n default"
          />
        </Wrapper>,
      );

      const button = container.querySelector('[data-testid="execute-action-button"]') as HTMLButtonElement;
      expect(button).not.toBeNull();
      expect(button.disabled).toBe(false);
    });

    it('uses defaultNamespace from props when -n flag is absent', () => {
      const Wrapper = createQueryWrapper();
      const { container } = render(
        <Wrapper>
          <ExecuteActionButton
            {...defaultProps}
            actionText="kubectl rollout restart deployment/my-app"
          />
        </Wrapper>,
      );

      const button = container.querySelector('[data-testid="execute-action-button"]') as HTMLButtonElement;
      expect(button).not.toBeNull();
      expect(button.disabled).toBe(false);
    });
  });

  describe('accessibility', () => {
    it('sets aria-label for disabled button', () => {
      const Wrapper = createQueryWrapper();
      const { container } = render(
        <Wrapper>
          <ExecuteActionButton
            {...defaultProps}
            actionText="review configuration files"
          />
        </Wrapper>,
      );

      const button = container.querySelector('[data-testid="execute-action-button"]') as HTMLButtonElement;
      expect(button).not.toBeNull();
      expect(button.getAttribute('aria-label')).toBe('Cannot auto-parse this action');
    });

    it('sets aria-label for enabled button', () => {
      const Wrapper = createQueryWrapper();
      const { container } = render(
        <Wrapper>
          <ExecuteActionButton
            {...defaultProps}
            actionText="kubectl rollout restart deployment/app -n default"
          />
        </Wrapper>,
      );

      const button = container.querySelector('[data-testid="execute-action-button"]') as HTMLButtonElement;
      expect(button).not.toBeNull();
      expect(button.getAttribute('aria-label')).toBe('Execute action');
    });
  });
});
