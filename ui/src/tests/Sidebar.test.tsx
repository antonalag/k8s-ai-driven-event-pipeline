import { describe, it, expect, afterEach } from 'vitest';
import { render, fireEvent, cleanup } from '@testing-library/react';
import { useState } from 'react';
import type { NavItemId } from '../types/dashboard';
import Sidebar from '../components/Sidebar';

/**
 * Unit tests for Sidebar component
 * Validates: Requirements 3.3, 3.4, 3.5, 7.3, 7.4
 */

afterEach(() => {
  cleanup();
});

/** Wrapper that manages Sidebar state like App Shell */
function SidebarWithState({ initialActive = 'dashboard' as NavItemId }) {
  const [activeNavItem, setActiveNavItem] = useState<NavItemId>(initialActive);
  return <Sidebar activeNavItem={activeNavItem} onNavItemClick={setActiveNavItem} />;
}

describe('Sidebar component', () => {
  describe('Requirement 3.3 - Navigation items with icons', () => {
    it('renders all 6 nav items with correct labels', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      const expectedLabels = [
        'Dashboard',
        'Pods & Nodes',
        'Log Explorer',
        'Metrics',
        'Distributed Traces',
        'AI Insight Engine',
      ];

      const navButtons = container.querySelectorAll('nav button');
      expect(navButtons.length).toBe(6);

      expectedLabels.forEach((label) => {
        const found = Array.from(navButtons).some((btn) =>
          btn.textContent?.includes(label)
        );
        expect(found, `Expected nav item "${label}" to be rendered`).toBe(true);
      });
    });

    it('renders correct Material Symbols icon for each nav item', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      const expectedIcons = [
        'dashboard',
        'view_module',
        'description',
        'analytics',
        'timeline',
        'auto_awesome',
      ];

      const iconElements = container.querySelectorAll('nav button .material-symbols-outlined');
      expect(iconElements.length).toBe(6);

      expectedIcons.forEach((icon, index) => {
        expect(iconElements[index].textContent).toBe(icon);
      });
    });
  });

  describe('Requirement 3.4 - Active nav item styling', () => {
    it('active nav item has nav-item-active class', () => {
      const { container } = render(
        <Sidebar activeNavItem="metrics" onNavItemClick={() => {}} />
      );

      const activeItems = container.querySelectorAll('.nav-item-active');
      expect(activeItems.length).toBe(1);
      expect(activeItems[0].textContent).toContain('Metrics');
    });

    it('click changes active item', () => {
      const { container } = render(<SidebarWithState initialActive="dashboard" />);

      // Initially Dashboard is active
      let activeItems = container.querySelectorAll('.nav-item-active');
      expect(activeItems.length).toBe(1);
      expect(activeItems[0].textContent).toContain('Dashboard');

      // Click on Log Explorer
      const navButtons = container.querySelectorAll('nav button');
      const logExplorerBtn = Array.from(navButtons).find((btn) =>
        btn.textContent?.includes('Log Explorer')
      );
      expect(logExplorerBtn).toBeDefined();
      fireEvent.click(logExplorerBtn!);

      // Now Log Explorer should be active
      activeItems = container.querySelectorAll('.nav-item-active');
      expect(activeItems.length).toBe(1);
      expect(activeItems[0].textContent).toContain('Log Explorer');
    });

    it('only clicked item becomes active, previous is deactivated', () => {
      const { container } = render(<SidebarWithState initialActive="pods-nodes" />);

      // Click AI Insight Engine
      const navButtons = container.querySelectorAll('nav button');
      const aiBtn = Array.from(navButtons).find((btn) =>
        btn.textContent?.includes('AI Insight Engine')
      );
      fireEvent.click(aiBtn!);

      const activeItems = container.querySelectorAll('.nav-item-active');
      expect(activeItems.length).toBe(1);
      expect(activeItems[0].textContent).toContain('AI Insight Engine');

      // Pods & Nodes should no longer be active
      const podsBtn = Array.from(navButtons).find((btn) =>
        btn.textContent?.includes('Pods & Nodes')
      );
      expect(podsBtn?.classList.contains('nav-item-active')).toBe(false);
    });
  });

  describe('Requirement 3.5 - Active Resources section', () => {
    it('renders Active Resources section with health dots', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      // Check the "Active Resources" label is rendered
      expect(container.textContent).toContain('Active Resources');

      // Check health dots are rendered (kd-rounded-full with color classes)
      const healthDots = container.querySelectorAll('[class*="kd-rounded-full"][class*="kd-bg-"]');
      expect(healthDots.length).toBeGreaterThanOrEqual(2);
    });

    it('renders resource cards with pod name, status, CPU, and memory', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      // Check analyzer-api resource
      expect(container.textContent).toContain('analyzer-api');
      expect(container.textContent).toContain('HEALTHY');
      expect(container.textContent).toContain('CPU: 12%');
      expect(container.textContent).toContain('Mem: 256Mi');

      // Check db-worker-7c2d resource
      expect(container.textContent).toContain('db-worker-7c2d');
      expect(container.textContent).toContain('CRITICAL');
      expect(container.textContent).toContain('CPU: 0.1%');
      expect(container.textContent).toContain('Mem: 48Mi');
    });

    it('healthy resource has primary dot color', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      const healthyDot = container.querySelector('[class*="kd-bg-primary"][class*="kd-rounded-full"]');
      expect(healthyDot).not.toBeNull();
    });

    it('critical resource has error dot color', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      const criticalDot = container.querySelector('[class*="kd-bg-error"][class*="kd-rounded-full"]');
      expect(criticalDot).not.toBeNull();
    });
  });

  describe('Requirement 7.3 - Hover interactions on nav items (group-hover)', () => {
    it('nav item buttons have group class for hover coordination', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      const navButtons = container.querySelectorAll('nav button');
      const buttonsWithGroup = Array.from(navButtons).filter((btn) =>
        btn.className.includes('kd-group')
      );
      expect(buttonsWithGroup.length).toBe(6);
    });

    it('nav item icons have group-hover:kd-scale-110 class', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      const icons = container.querySelectorAll('nav button .material-symbols-outlined');
      const iconsWithScaleHover = Array.from(icons).filter((icon) =>
        icon.className.includes('group-hover:kd-scale-110')
      );
      expect(iconsWithScaleHover.length).toBe(6);
    });

    it('non-AI nav item labels have group-hover:kd-translate-x-1 class', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      // Non-AI items (first 5) should have translate-x-1 on hover
      const navButtons = container.querySelectorAll('nav button');
      const nonAiButtons = Array.from(navButtons).slice(0, 5);
      nonAiButtons.forEach((btn) => {
        const label = btn.querySelector('span:not(.material-symbols-outlined)');
        expect(
          label?.className.includes('group-hover:kd-translate-x-1'),
          `Non-AI nav item label should have group-hover:kd-translate-x-1`
        ).toBe(true);
      });
    });
  });

  describe('Requirement 7.4 - Hover interactions on Active Resources cards', () => {
    it('healthy resource card has hover:kd-border-primary/40 class for primary-tinted border', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      // The healthy resource card (analyzer-api) should have hover:kd-border-primary/40
      const cards = container.querySelectorAll('nav [class*="kd-p-3"]');
      const healthyCard = Array.from(cards).find((card) =>
        card.textContent?.includes('analyzer-api')
      );
      expect(healthyCard).not.toBeNull();
      expect(healthyCard!.className).toContain('hover:kd-border-primary/40');
    });

    it('resource cards have hover:kd-shadow for subtle shadow effect', () => {
      const { container } = render(
        <Sidebar activeNavItem="dashboard" onNavItemClick={() => {}} />
      );

      const cards = container.querySelectorAll('nav [class*="kd-p-3"]');
      const healthyCard = Array.from(cards).find((card) =>
        card.textContent?.includes('analyzer-api')
      );
      expect(healthyCard).not.toBeNull();
      expect(healthyCard!.className).toContain('hover:kd-shadow');
    });
  });
});
