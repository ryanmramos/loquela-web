import AxeBuilder from '@axe-core/playwright';
import { expect, Page, Route, test } from '@playwright/test';

const activeSession = {
  userId: '529b7902-040a-4a40-8905-aa4ee06d2413',
  displayName: 'Local Learner',
  email: 'learner@local.loquela.test',
  sourceLanguage: 'es',
  definitionLanguage: 'en',
  accountState: 'ACTIVE',
  linkedAccounts: { google: true, whatsapp: false },
};

test('anonymous users see an accessible sign-in page with local accounts', async ({ page }) => {
  await mockAuthentication(page);
  await page.goto('/');

  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fapp/u);
  await expect(
    page.getByRole('heading', { name: 'Turn what you read into what you remember.' }),
  ).toBeVisible();
  await expect(page.getByRole('link', { name: 'Continue with Google' })).toHaveAttribute(
    'href',
    '/api/v1/auth/google?returnTo=%2Fapp',
  );
  await expect(page.getByRole('button', { name: 'Local Learner' })).toBeVisible();

  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});

test('local login creates activity access and logout survives browser back', async ({ page }) => {
  const authentication = await mockAuthentication(page);
  await page.goto('/login');
  await page.getByRole('button', { name: 'Local Learner' }).click();

  await expect(page).toHaveURL(/\/app$/u);
  await expect(page.getByRole('heading', { name: 'Welcome back, Local Learner.' })).toBeVisible();
  await expect(page.getByText('learner@local.loquela.test')).toBeVisible();

  await page.getByRole('button', { name: 'Open user menu' }).click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/u);
  expect(authentication.isAuthenticated()).toBe(false);

  await page.goBack();
  await expect(page).toHaveURL(/\/login/u);
  await expect(
    page.getByRole('heading', { name: 'Turn what you read into what you remember.' }),
  ).toBeVisible();
});

async function mockAuthentication(page: Page): Promise<{ isAuthenticated: () => boolean }> {
  let authenticated = false;

  await page.route('**/api/v1/session/logout', async (route: Route) => {
    authenticated = false;
    await route.fulfill({ status: 204 });
  });

  await page.route('**/api/v1/session', async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(activeSession),
      });
      return;
    }
    await route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'https://loquela.app/problems/401',
        title: 'Authentication required',
        status: 401,
        code: 'AUTHENTICATION_REQUIRED',
        correlationId: 'playwright-session',
        fieldErrors: [],
      }),
    });
  });

  await page.route('**/api/v1/auth/local-login', async (route: Route) => {
    authenticated = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(activeSession),
    });
  });

  return { isAuthenticated: () => authenticated };
}
