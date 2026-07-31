import { test, expect } from '@playwright/test';

test.describe('Remediation — Action Buttons', () => {
  test('analysis card should display actionable remediation buttons', async ({ page }) => {
    await page.goto('/');

    // Wait for at least one analysis card to be rendered by the pipeline
    const actionBlock = page.locator('[data-testid="recommended-actions"]');
    await expect(actionBlock.first()).toBeVisible({ timeout: 20_000 });

    // Each action item renders a "Copy" button — verify at least one exists
    const copyButtons = page.locator('[data-testid^="copy-button-"]');
    await expect(copyButtons.first()).toBeVisible();
    await expect(copyButtons.first()).toBeEnabled();

    // Check for Execute buttons (rendered when action is parseable and executable)
    const executeButtons = page.locator('[data-testid="execute-action-button"]');
    const executeCount = await executeButtons.count();

    if (executeCount > 0) {
      await expect(executeButtons.first()).toBeVisible();
      await expect(executeButtons.first()).toBeEnabled();
    }
  });

  test('Copy button should be clickable without crashing the UI', async ({ page }) => {
    await page.goto('/');

    // Wait for action blocks to render
    const actionBlock = page.locator('[data-testid="recommended-actions"]');
    await expect(actionBlock.first()).toBeVisible({ timeout: 20_000 });

    const copyButton = page.locator('[data-testid^="copy-button-"]').first();
    await expect(copyButton).toBeVisible();

    // Grant clipboard permissions for headless Chromium
    await page.context().grantPermissions(['clipboard-write', 'clipboard-read']);

    // Click the copy button — should not crash
    await copyButton.click();

    // Wait briefly for any state change
    await page.waitForTimeout(500);

    // Page should remain functional — main content still visible, no error overlay
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('main').getByRole('alert')).toHaveCount(0);
  });
});
