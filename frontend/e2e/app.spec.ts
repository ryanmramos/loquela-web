import { expect, test } from '@playwright/test';

test('loads the Loquela application shell', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle('Loquela');
  await expect(
    page.getByRole('heading', { name: 'Learn from what matters to you.' }),
  ).toBeVisible();
});
