import { expect, test } from '@playwright/test';

import { mockAuthenticatedE2eRuntimeConfig } from './runtime-config';

test('the former foundation URL is not an application route', async ({ page }) => {
  await mockAuthenticatedE2eRuntimeConfig(page);
  await page.route('**/api/v1/session', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({ status: 401, title: 'Unauthorized' }),
    }),
  );

  await page.goto('/foundation');

  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fapp/u);
  await expect(page.getByRole('heading', { name: 'Keep the words you meet.' })).toBeVisible();
});

test('fails safely when runtime configuration is unavailable', async ({ page }) => {
  await page.route('**/config.json', (route) =>
    route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: '{}',
    }),
  );

  await page.goto('/');

  await expect(page).toHaveTitle('Loquela configuration error');
  await expect(page.getByRole('heading', { name: 'Loquela could not start' })).toBeVisible();
  await expect(page.locator('app-root')).toHaveCount(0);
});
