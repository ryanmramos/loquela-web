import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test('loads the Loquela design foundation', async ({ page }) => {
  await page.goto('/foundation');

  await expect(page).toHaveTitle('Design foundation · Loquela');
  await expect(page.getByRole('heading', { name: 'Your language workspace' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toBeVisible();
});

test('shell and component foundation have no detectable accessibility violations', async ({
  page,
}) => {
  await page.goto('/foundation');
  await expect(page.getByRole('heading', { name: 'Your language workspace' })).toBeVisible();

  const results = await new AxeBuilder({ page }).analyze();

  expect(results.violations).toEqual([]);
});

test('skip link moves keyboard focus to main content', async ({ page }) => {
  await page.goto('/foundation');
  await expect(page.getByRole('heading', { name: 'Your language workspace' })).toBeVisible();

  await page.keyboard.press('Tab');
  const skipLink = page.getByRole('link', { name: 'Skip to main content' });
  await expect(skipLink).toBeFocused();
  await page.keyboard.press('Enter');

  await expect(page.locator('#main-content')).toBeFocused();
});

test('dialog traps focus, closes with Escape, and restores the trigger', async ({ page }) => {
  await page.goto('/foundation');

  const trigger = page.getByRole('button', { name: 'Add a source' });
  await trigger.click();

  await expect(page.getByRole('dialog', { name: 'Confirm source' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeFocused();
  await page.keyboard.press('Escape');

  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

test('search and user menu are fully keyboard operable with visible focus', async ({ page }) => {
  await page.goto('/foundation');
  await expect(page.getByRole('heading', { name: 'Your language workspace' })).toBeVisible();

  const search = page.getByRole('searchbox', { name: 'Search your workspace' });
  await search.focus();
  await expect(search).toBeFocused();
  const searchFocusRing = await page
    .locator('.workspace-search')
    .evaluate((element) => getComputedStyle(element).boxShadow);
  expect(searchFocusRing).not.toBe('none');
  await search.fill('sobremesa');
  await search.press('Enter');
  await expect(page.getByText('Search submitted')).toBeVisible();

  const menuTrigger = page.getByRole('button', { name: 'Open user menu' });
  await menuTrigger.focus();
  await menuTrigger.press('Enter');
  await expect(page.getByRole('menuitem', { name: 'Profile settings' })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(menuTrigger).toBeFocused();
});

for (const viewport of [
  { label: 'mobile', width: 320, height: 720 },
  { label: 'tablet', width: 768, height: 900 },
  { label: 'desktop', width: 1440, height: 1000 },
] as const) {
  test(`renders without horizontal overflow at ${viewport.label} width`, async ({ page }) => {
    await page.setViewportSize(viewport);
    await page.goto('/foundation');

    const layout = await page.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }));
    expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth);

    if (viewport.label === 'mobile') {
      await expect(
        page.getByRole('navigation', { name: 'Mobile primary navigation' }),
      ).toBeVisible();
      await expect(
        page.getByRole('navigation', { name: 'Primary navigation', exact: true }),
      ).toBeHidden();
    } else {
      await expect(
        page.getByRole('navigation', { name: 'Primary navigation', exact: true }),
      ).toBeVisible();
      await expect(
        page.getByRole('navigation', { name: 'Mobile primary navigation' }),
      ).toBeHidden();
    }
  });
}

test('reduced motion removes nonessential animation', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/foundation');

  await expect(page.locator('app-skeleton .skeleton').first()).toHaveCSS('animation-name', 'none');
  await expect(page.locator('.state__spinner')).toHaveCSS('animation-name', 'none');
});

test('fails safely when runtime configuration is unavailable', async ({ page }) => {
  await page.route('**/config.json', (route) =>
    route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: '{}',
    }),
  );

  await page.goto('/foundation');

  await expect(page).toHaveTitle('Loquela configuration error');
  await expect(page.getByRole('heading', { name: 'Loquela could not start' })).toBeVisible();
  await expect(page.locator('app-root')).toHaveCount(0);
});
