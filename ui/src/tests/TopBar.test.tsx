import { describe, it, expect, afterEach } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import TopBar from '../components/TopBar';

/**
 * Unit tests for TopBar component.
 * Validates: Requirements 4.2, 4.3, 4.5, 4.6
 */

const defaultBreadcrumbs = [
  { label: 'us-east-1-prod' },
  { label: 'kube-system' },
  { label: 'analyzer-api-7d4f8b', isActive: true },
];

afterEach(() => {
  cleanup();
});

describe('TopBar component', () => {
  describe('Breadcrumbs with chevron separators (Requirement 4.2)', () => {
    it('renders all breadcrumb labels', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      for (const crumb of defaultBreadcrumbs) {
        expect(container.textContent).toContain(crumb.label);
      }
    });

    it('renders chevron_right separators between breadcrumb items', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      const chevrons = container.querySelectorAll('.material-symbols-outlined');
      const chevronTexts = Array.from(chevrons)
        .map((el) => el.textContent?.trim())
        .filter((text) => text === 'chevron_right');

      // There should be N-1 chevrons for N breadcrumbs
      expect(chevronTexts.length).toBe(defaultBreadcrumbs.length - 1);
    });

    it('does not render a chevron before the first breadcrumb', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      const nav = container.querySelector('nav[aria-label="Breadcrumb"]');
      expect(nav).not.toBeNull();

      // The first span child should not contain chevron_right
      const firstSpan = nav!.querySelector('span');
      const firstChevron = firstSpan?.querySelector('.material-symbols-outlined');
      // First breadcrumb item should not have a chevron before it
      if (firstChevron) {
        expect(firstChevron.textContent?.trim()).not.toBe('chevron_right');
      }
    });

    it('applies active styling to the active breadcrumb', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      // The active breadcrumb should have bold font styling
      const spans = container.querySelectorAll('span');
      const activeBreadcrumb = Array.from(spans).find(
        (span) => span.textContent === 'analyzer-api-7d4f8b'
      );

      expect(activeBreadcrumb).toBeDefined();
      expect(activeBreadcrumb!.className).toContain('kd-font-bold');
    });
  });

  describe('LIVE CONNECTION indicator (Requirement 4.3)', () => {
    it('renders "LIVE CONNECTION" text', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      expect(container.textContent).toContain('LIVE CONNECTION');
    });

    it('renders with pulsing animation class', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      // The live connection container should have pulse-soft animation
      const liveIndicator = Array.from(container.querySelectorAll('div')).find(
        (el) => el.textContent?.includes('LIVE CONNECTION') && el.className.includes('kd-animate-pulse-soft')
      );

      expect(liveIndicator).toBeDefined();
    });

    it('renders a green pulsing dot', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      // Find the dot element inside the live connection indicator
      const dot = container.querySelector('.kd-bg-primary.kd-rounded-full.kd-w-2.kd-h-2');
      expect(dot).not.toBeNull();
    });
  });

  describe('Utility icons (Requirement 4.5)', () => {
    it('renders terminal icon', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      const icons = container.querySelectorAll('.material-symbols-outlined');
      const terminalIcon = Array.from(icons).find(
        (el) => el.textContent?.trim() === 'terminal'
      );

      expect(terminalIcon).toBeDefined();
    });

    it('renders notifications icon', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      const icons = container.querySelectorAll('.material-symbols-outlined');
      const notificationsIcon = Array.from(icons).find(
        (el) => el.textContent?.trim() === 'notifications'
      );

      expect(notificationsIcon).toBeDefined();
    });

    it('renders settings icon', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      const icons = container.querySelectorAll('.material-symbols-outlined');
      const settingsIcon = Array.from(icons).find(
        (el) => el.textContent?.trim() === 'settings'
      );

      expect(settingsIcon).toBeDefined();
    });

    it('renders profile avatar (person icon)', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      const icons = container.querySelectorAll('.material-symbols-outlined');
      const personIcon = Array.from(icons).find(
        (el) => el.textContent?.trim() === 'person'
      );

      expect(personIcon).toBeDefined();
    });
  });

  describe('No dead links (Requirement 4.6)', () => {
    it('does NOT render any <a href="#"> dead links', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      const links = container.querySelectorAll('a[href="#"]');
      expect(links.length).toBe(0);
    });

    it('does NOT render any anchor elements at all', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      // TopBar should not have any anchor tags since all navigation
      // is handled through breadcrumb spans and icon buttons
      const anchors = container.querySelectorAll('a');
      expect(anchors.length).toBe(0);
    });
  });
});
