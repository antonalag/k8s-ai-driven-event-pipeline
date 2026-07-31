import { test, expect } from '@playwright/test';

test.describe('Error States — Backend Unavailable', () => {
  test('should display connection error with retry button when API fails', async ({ page }) => {
    // Intercept all API calls and simulate network failure
    await page.route('**/api/v1/analyses**', (route) => {
      route.abort('connectionrefused');
    });

    await page.goto('/');

    // Wait for the error state to appear (TanStack Query will retry a few times)
    const errorAlert = page.getByRole('alert');
    await expect(errorAlert).toBeVisible({ timeout: 30_000 });

    // Verify error title is displayed
    await expect(errorAlert).toContainText('Connection Error');

    // Verify retry button exists and is interactive
    const retryButton = page.getByRole('button', { name: 'Retry' });
    await expect(retryButton).toBeVisible();
    await expect(retryButton).toBeEnabled();
  });

  test('retry button should trigger a new fetch attempt', async ({ page }) => {
    // Block all API calls permanently
    await page.route('**/api/v1/analyses**', (route) => {
      route.abort('connectionrefused');
    });

    await page.goto('/');

    // Wait for error state (TanStack Query retries internally then shows error)
    const errorAlert = page.getByRole('alert');
    await expect(errorAlert).toBeVisible({ timeout: 45_000 });

    // Now unblock the route to allow successful response
    await page.unroute('**/api/v1/analyses**');
    await page.route('**/api/v1/analyses**', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    // Click retry
    const retryButton = page.getByRole('button', { name: 'Retry' });
    await retryButton.click();

    // Error should disappear after successful refetch
    await expect(errorAlert).not.toBeVisible({ timeout: 10_000 });
  });
});
