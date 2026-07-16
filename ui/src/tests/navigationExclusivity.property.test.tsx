import { describe, it, expect, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { render, fireEvent, cleanup } from '@testing-library/react';
import { useState } from 'react';
import type { NavItemId } from '../types/dashboard';
import Sidebar from '../components/Sidebar';

// Feature: ui-observability-dashboard, Property 1: Navigation exclusivity

/**
 * **Validates: Requirements 3.4**
 *
 * For any NavItemId, when clicked, exactly one item has active state matching that ID.
 */

const navItemIdArb = fc.constantFrom<NavItemId>(
  'dashboard', 'log-explorer', 'ai-insight-engine'
);

const NAV_LABELS: Record<NavItemId, string> = {
  'dashboard': 'Dashboard',
  'log-explorer': 'Log Explorer',
  'ai-insight-engine': 'AI Insight Engine',
};

/** Wrapper component that wires Sidebar state like the real App Shell does */
function SidebarWrapper({ initialActive }: { initialActive: NavItemId }) {
  const [activeNavItem, setActiveNavItem] = useState<NavItemId>(initialActive);

  return (
    <Sidebar
      activeNavItem={activeNavItem}
      onNavItemClick={setActiveNavItem}
    />
  );
}

afterEach(() => {
  cleanup();
});

describe('Property 1: Navigation exclusivity', () => {
  it('for any NavItemId, when clicked, exactly one item has active state matching that ID', () => {
    fc.assert(
      fc.property(navItemIdArb, (clickedId: NavItemId) => {
        cleanup();
        const { container } = render(
          <Sidebar activeNavItem={clickedId} onNavItemClick={() => {}} />
        );

        // Find all nav-item-active elements
        const activeItems = container.querySelectorAll('.nav-item-active');

        // Exactly one item should have the active state
        expect(activeItems.length).toBe(1);

        // The active item should contain the label matching the clicked ID
        const activeElement = activeItems[0];
        expect(activeElement.textContent).toContain(NAV_LABELS[clickedId]);
      }),
      { numRuns: 100 },
    );
  });

  it('clicking a nav item transitions it to the sole active item', () => {
    fc.assert(
      fc.property(
        navItemIdArb,
        navItemIdArb,
        (initialId: NavItemId, clickedId: NavItemId) => {
          cleanup();
          const { container } = render(
            <SidebarWrapper initialActive={initialId} />
          );

          // Find and click the target nav item's button
          const targetLabel = NAV_LABELS[clickedId];
          const buttons = container.querySelectorAll('nav button');
          const targetButton = Array.from(buttons).find(
            (btn) => btn.textContent?.includes(targetLabel)
          );

          expect(targetButton).toBeDefined();
          fireEvent.click(targetButton!);

          // After click, exactly one item should be active
          const activeItems = container.querySelectorAll('.nav-item-active');
          expect(activeItems.length).toBe(1);

          // The active item should be the one that was clicked
          const activeElement = activeItems[0];
          expect(activeElement.textContent).toContain(targetLabel);
        },
      ),
      { numRuns: 100 },
    );
  });
});
