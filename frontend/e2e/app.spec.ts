import { expect, test } from '@playwright/test';

test('loads the Loquela application shell', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle('Welcome · Loquela');
  await expect(
    page.getByRole('heading', { name: 'Learn from what matters to you.' }),
  ).toBeVisible();
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
