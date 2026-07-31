import { test, expect } from '@playwright/test';

test.describe('Navigation — Sidebar', () => {
  test('should navigate to Audit Log tab when clicked', async ({ page }) => {
    await page.goto('/');

    // Click the Audit Log button in the sidebar
    const auditLogNav = page.locator('aside button', { hasText: 'Audit Log' });
    await expect(auditLogNav).toBeVisible();
    await auditLogNav.click();

    // AuditLogView renders its heading
    await expect(page.locator('main h2', { hasText: 'Audit Log' })).toBeVisible();

    // The view shows either the table or the empty state — no crash
    const table = page.locator('main table');
    const emptyState = page.getByText('No resolved analyses yet');
    await expect(table.or(emptyState)).toBeVisible();

    // Verify no ErrorBoundary alert was triggered
    await expect(page.locator('main').getByRole('alert')).toHaveCount(0);
  });

  test('should navigate back to Dashboard from Audit Log', async ({ page }) => {
    await page.goto('/');

    // Navigate to Audit Log
    await page.locator('aside button', { hasText: 'Audit Log' }).click();
    await expect(page.locator('main h2', { hasText: 'Audit Log' })).toBeVisible();

    // Navigate back to Dashboard
    await page.locator('aside button', { hasText: 'Dashboard' }).click();

    // Audit Log heading should no longer be visible
    await expect(page.locator('main h2', { hasText: 'Audit Log' })).not.toBeVisible();

    // Main content area is rendered without errors
    await expect(page.locator('main')).toBeVisible();
  });
});
