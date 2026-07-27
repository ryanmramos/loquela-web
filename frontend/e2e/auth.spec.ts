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

test('dashboard renders aggregate counts, filtered links, activity, and quick capture', async ({
  page,
}) => {
  await mockAuthentication(page, true);
  await page.goto('/app');

  await expect(page.getByRole('heading', { name: 'Welcome back, Local Learner.' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Cards to study/u })).toHaveAttribute(
    'href',
    '/app/cards?practice=unpracticed',
  );
  await expect(page.getByRole('link', { name: /Ready sources/u })).toHaveAttribute(
    'href',
    '/app/sources?status=READY',
  );
  await expect(page.getByText('Easy Spanish transcript')).toBeVisible();

  await page.getByLabel('Source text').fill('Una frase desde Playwright.');
  await page.getByRole('button', { name: 'Add to sources' }).click();
  await expect(page.getByText('Your text is being indexed.')).toBeVisible();

  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});

test('dashboard renders the zero-data onboarding state', async ({ page }) => {
  await mockAuthentication(page, true, {
    ...dashboardSnapshot,
    cards: {
      status: 'AVAILABLE',
      cardsToStudy: 0,
      totalCards: 0,
      newCardsThisWeek: 0,
    },
    sources: {
      status: 'AVAILABLE',
      totalSources: 0,
      readySources: 0,
      processingSources: 0,
    },
    jobs: { status: 'AVAILABLE', activeJobs: 0 },
    activity: { status: 'AVAILABLE', items: [] },
  });
  await page.goto('/app');

  await expect(
    page.getByRole('heading', { name: 'Start with something worth remembering' }),
  ).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Quick capture' })).toBeVisible();
});

test('dashboard survives partial and large representative sections', async ({ page }) => {
  await mockAuthentication(page, true, {
    ...dashboardSnapshot,
    cards: {
      status: 'UNAVAILABLE',
      cardsToStudy: null,
      totalCards: null,
      newCardsThisWeek: null,
    },
    sources: {
      status: 'AVAILABLE',
      totalSources: 9_876_543,
      readySources: 8_765_432,
      processingSources: 0,
    },
    activity: { status: 'AVAILABLE', items: [] },
  });
  await page.goto('/app');

  await expect(page.getByLabel('Unavailable').first()).toBeVisible();
  await expect(page.getByText('8,765,432')).toBeVisible();
  await expect(page.getByText('No activity yet')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Quick capture' })).toBeVisible();
});

test('mobile dashboard navigation and global lookup are keyboard operable', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 });
  await mockAuthentication(page, true);
  await page.goto('/app');

  const mobileNavigation = page.getByRole('navigation', { name: 'Mobile primary navigation' });
  await expect(mobileNavigation).toBeVisible();
  const sourcesLink = mobileNavigation.getByRole('link', { name: 'Sources' });
  await sourcesLink.focus();
  await expect(sourcesLink).toBeFocused();
  await sourcesLink.press('Enter');
  await expect(page).toHaveURL(/\/app\/sources$/u);

  const search = page.getByRole('searchbox', { name: 'Search your workspace' });
  await search.fill('sobremesa');
  await search.press('Enter');
  await expect(page).toHaveURL(/\/app\/lookup\?query=sobremesa$/u);
  await expect(page.getByText('query: sobremesa')).toBeVisible();
});

async function mockAuthentication(
  page: Page,
  initiallyAuthenticated = false,
  dashboard = dashboardSnapshot,
): Promise<{ isAuthenticated: () => boolean }> {
  let authenticated = initiallyAuthenticated;

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

  await page.route('**/api/v1/dashboard', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(dashboard),
    });
  });

  await page.route('**/api/v1/sources', async (route: Route) => {
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        sourceId: '5b8d1092-9aa3-428a-8109-e94291a098e7',
        reference: 'src_playwright',
        status: 'RECEIVED',
        characterCount: 27,
      }),
    });
  });

  return { isAuthenticated: () => authenticated };
}

const dashboardSnapshot = {
  generatedAt: new Date().toISOString(),
  languagePair: { sourceLanguage: 'es', definitionLanguage: 'en' },
  cards: {
    status: 'AVAILABLE',
    cardsToStudy: 12,
    totalCards: 384,
    newCardsThisWeek: 16,
  },
  sources: {
    status: 'AVAILABLE',
    totalSources: 26,
    readySources: 24,
    processingSources: 2,
  },
  jobs: { status: 'AVAILABLE', activeJobs: 2 },
  activity: {
    status: 'AVAILABLE',
    items: [
      {
        kind: 'SOURCE',
        title: 'Easy Spanish transcript',
        detail: 'YouTube source',
        status: 'READY',
        occurredAt: new Date().toISOString(),
        target: '/app/sources/src_spanish',
      },
    ],
  },
};
