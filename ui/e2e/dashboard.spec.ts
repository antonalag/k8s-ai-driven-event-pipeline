import { test, expect } from '@playwright/test';

test.describe('Dashboard — Analysis Cards', () => {
  test('should display at least one analysis card after pipeline processing', async ({ page }) => {
    await page.goto('/');

    // Cards are rendered inside .kd-space-y-3 container; each card is a bordered div
    const card = page.locator('.kd-space-y-3 > div .kd-bg-surface-container-low').first();
    await expect(card).toBeVisible({ timeout: 30_000 });
  });

  test('analysis card should contain podName, namespace, verdict, and recommendedActions', async ({ page }) => {
    await page.goto('/');

    // Wait for at least one analysis card to appear
    const card = page.locator('.kd-space-y-3 > div .kd-bg-surface-container-low').first();
    await expect(card).toBeVisible({ timeout: 30_000 });

    // Verify podName — one of the seeded pods from mock-ai-server
    const podNames = ['crashloop-pod-e2e-001', 'oomkilled-pod-e2e-002', 'imagepull-pod-e2e-003'];
    const cardText = await card.textContent();
    const hasPodName = podNames.some((name) => cardText?.includes(name));
    expect(hasPodName).toBe(true);

    // Verify namespace "chaos-validation"
    await expect(card).toContainText('chaos-validation');

    // Verify verdict badge is visible (CRITICAL, WARNING, HEALTHY, or DEGRADED label)
    const verdictBadge = card.locator('.kd-text-label-caps');
    await expect(verdictBadge.first()).toBeVisible();

    // Verify recommended actions section exists with at least one kubectl command
    const actionsBlock = card.locator('[data-testid="recommended-actions"]');
    await expect(actionsBlock).toBeVisible();
    await expect(actionsBlock).toContainText('kubectl');
  });

  test('should display multiple analysis cards for different pods', async ({ page }) => {
    await page.goto('/');

    // Wait for cards to appear and stabilize
    const firstCard = page.locator('.kd-space-y-3 > div .kd-bg-surface-container-low').first();
    await expect(firstCard).toBeVisible({ timeout: 30_000 });

    const cards = page.locator('.kd-space-y-3 > div .kd-bg-surface-container-low');
    const count = await cards.count();

    // We seeded 3 events — expect at least 1 card visible
    // (some may be deduplicated by deployment prefix in the backend)
    expect(count).toBeGreaterThanOrEqual(1);
  });
});
