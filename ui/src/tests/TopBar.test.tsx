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

    it('renders with pulsing animation class on the dot element', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      // The pulsing dot has the animate-pulse-soft class
      const dot = container.querySelector('.kd-animate-pulse-soft');
      expect(dot).not.toBeNull();
    });

    it('renders a green pulsing dot', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      // Find the dot element inside the live connection indicator
      const dot = container.querySelector('.kd-bg-primary.kd-rounded-full.kd-w-2.kd-h-2');
      expect(dot).not.toBeNull();
    });
  });

  describe('Header structure (Requirement 4.5)', () => {
    it('renders as a header element with sticky positioning', () => {
      const { container } = render(<TopBar breadcrumbs={defaultBreadcrumbs} />);

      const header = container.querySelector('header');
      expect(header).not.toBeNull();
      expect(header!.className).toContain('kd-sticky');
      expect(header!.className).toContain('kd-top-0');
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
