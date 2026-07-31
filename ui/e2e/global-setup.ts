import type { FullConfig } from '@playwright/test';

const BASE_URL = 'http://localhost:3000';
const POLL_INTERVAL_MS = 2000;
const TIMEOUT_MS = 60_000;

async function globalSetup(_config: FullConfig): Promise<void> {
  const startTime = Date.now();
  console.log(`[E2E Setup] Waiting for UI at ${BASE_URL}...`);

  while (Date.now() - startTime < TIMEOUT_MS) {
    try {
      const response = await fetch(BASE_URL);
      if (response.ok) {
        console.log(`[E2E Setup] UI is ready (${response.status}) — elapsed ${Date.now() - startTime}ms`);
        return;
      }
    } catch {
      // Server not ready yet — continue polling
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }

  throw new Error(`[E2E Setup] Timeout: UI at ${BASE_URL} did not respond within ${TIMEOUT_MS}ms`);
}

export default globalSetup;
