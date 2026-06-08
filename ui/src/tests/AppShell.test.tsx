import { describe, it, expect, afterEach } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import App from '../App';

/**
 * Unit tests for App Shell composition
 * Validates: Requirements 7.1, 8.5
 */

afterEach(() => {
  cleanup();
});

describe('App Shell composition', () => {
  describe('Requirement 8.5: All utility classes use kd- prefix', () => {
    it('all Tailwind utility classes in the App Shell use the kd- prefix', () => {
      const { container } = render(<App />);

      // Collect all class attributes from all elements in the rendered App Shell
      const allElements = container.querySelectorAll('[class]');

      allElements.forEach((element) => {
        const classes = element.getAttribute('class') ?? '';
        const classList = classes.split(/\s+/).filter((c) => c.length > 0);

        classList.forEach((cls) => {
          // Skip non-Tailwind classes (global CSS classes, animation-related, etc.)
          const nonTailwindPatterns = [
            'nav-item-active',
            'ai-glow',
            'panel-depth',
            'ai-panel-depth',
            'shimmer-mask',
            'cursor-blink',
            'group',
            'material-symbols-outlined',
          ];

          const isNonTailwindClass = nonTailwindPatterns.some(
            (pattern) => cls === pattern || cls.startsWith(pattern)
          );

          if (isNonTailwindClass) return;

          // Strip variant prefixes (hover:, focus:, group-hover:, etc.)
          // Valid Tailwind with kd- prefix uses: hover:kd-bg-primary, group-hover:kd-scale-110
          const strippedCls = cls.replace(/^(?:hover:|focus:|group-hover:|active:|disabled:)+/, '');

          // Tailwind utility classes should start with kd- prefix after variant stripping
          const tailwindPatterns = /^(flex|grid|p-|m-|w-|h-|text-|bg-|border-|rounded|overflow|col-|row-|gap-|font-|animate-|sticky|fixed|relative|absolute|top-|left-|right-|bottom-|z-|items-|justify-|space-|opacity-|shadow-|transition-|duration-|cursor-|scale-|translate-|transform|inline-|block|hidden|shrink|grow|self-|place-|min-|max-|tracking-|leading-|whitespace-|truncate|break-|select-|pointer-|backdrop-)/;

          if (tailwindPatterns.test(strippedCls)) {
            // After stripping variant prefix, class must have kd- prefix
            expect(strippedCls).toMatch(/^kd-/);
          }
        });
      });
    });

    it('root container uses kd- prefixed classes only', () => {
      const { container } = render(<App />);
      const rootDiv = container.firstElementChild as HTMLElement;

      const classes = rootDiv.className.split(/\s+/);
      const tailwindClasses = classes.filter((c) => c.length > 0);

      tailwindClasses.forEach((cls) => {
        expect(cls.startsWith('kd-')).toBe(true);
      });
    });
  });

  describe('Requirement 7.1: Staggered reveal animation delays', () => {
    it('content panels have kd-animate-reveal class', () => {
      const { container } = render(<App />);

      const revealPanels = container.querySelectorAll('.kd-animate-reveal');
      expect(revealPanels.length).toBeGreaterThanOrEqual(2);
    });

    it('panels have incremental animation delay values', () => {
      const { container } = render(<App />);

      const revealPanels = container.querySelectorAll('.kd-animate-reveal');
      const delays: number[] = [];

      revealPanels.forEach((panel) => {
        const style = (panel as HTMLElement).style;
        const delay = style.animationDelay;
        if (delay) {
          delays.push(parseFloat(delay));
        }
      });

      // Should have at least 2 panels with delays
      expect(delays.length).toBeGreaterThanOrEqual(2);

      // Delays should be staggered (each subsequent delay is greater)
      for (let i = 1; i < delays.length; i++) {
        expect(delays[i]).toBeGreaterThan(delays[i - 1]);
      }
    });

    it('panels have animationFillMode set to backwards for reveal effect', () => {
      const { container } = render(<App />);

      const revealPanels = container.querySelectorAll('.kd-animate-reveal');

      revealPanels.forEach((panel) => {
        const style = (panel as HTMLElement).style;
        expect(style.animationFillMode).toBe('backwards');
      });
    });
  });

  describe('Requirement 7.1: CSS Grid layout has 12-column and 6-row configuration', () => {
    it('content grid has kd-grid-cols-12 class for 12-column layout', () => {
      const { container } = render(<App />);

      const gridElement = container.querySelector('.kd-grid-cols-12');
      expect(gridElement).not.toBeNull();
    });

    it('content grid has kd-grid-rows-6 class for 6-row layout', () => {
      const { container } = render(<App />);

      const gridElement = container.querySelector('.kd-grid-rows-6');
      expect(gridElement).not.toBeNull();
    });

    it('content grid uses kd-grid class', () => {
      const { container } = render(<App />);

      const gridElement = container.querySelector('.kd-grid');
      expect(gridElement).not.toBeNull();
    });

    it('LogViewer panel spans 12 columns and 3 rows within the content grid', () => {
      const { container } = render(<App />);

      const gridElement = container.querySelector('.kd-grid-cols-12.kd-grid-rows-6');
      expect(gridElement).not.toBeNull();

      // Direct children of the grid should have col-span-12 and row-span-3
      const directPanels = gridElement!.querySelectorAll(':scope > .kd-col-span-12.kd-row-span-3');
      expect(directPanels.length).toBeGreaterThanOrEqual(1);
    });

    it('content grid direct children are the two main panels', () => {
      const { container } = render(<App />);

      const gridElement = container.querySelector('.kd-grid-cols-12.kd-grid-rows-6');
      expect(gridElement).not.toBeNull();

      // Both direct children of the grid should span full width and 3 rows
      const directPanels = gridElement!.querySelectorAll(':scope > .kd-col-span-12.kd-row-span-3');
      expect(directPanels.length).toBe(2);
    });
  });
});
