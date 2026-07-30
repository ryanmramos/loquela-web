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
  await expect(page.getByLabel('Term to look up')).toHaveValue('sobremesa');
});

test('lookup polling reports success and terminal lookup failures', async ({ page }) => {
  await mockAuthentication(page, true);
  const polls = new Map<string, number>();

  await page.route('**/api/v1/lookups', async (route: Route) => {
    const request = route.request();
    if (request.method() !== 'POST') {
      await route.fallback();
      return;
    }
    const term = (request.postDataJSON() as { term: string }).term;
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({ reference: `lookup-${term}`, expiresAt: new Date().toISOString() }),
    });
  });
  await page.route('**/api/v1/lookups/*', async (route: Route) => {
    const reference = route.request().url().split('/').at(-1) ?? '';
    const count = (polls.get(reference) ?? 0) + 1;
    polls.set(reference, count);
    const common = {
      reference,
      term: reference.replace('lookup-', ''),
      languagePair: { sourceLanguage: 'es', definitionLanguage: 'en' },
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    };
    if (reference === 'lookup-unknown') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...common,
          status: 'FAILED',
          errorCode: 'DEFINITION_NOT_FOUND',
          contexts: [],
          senses: [],
        }),
      });
      return;
    }
    if (reference === 'lookup-aislado') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...common,
          status: 'FAILED',
          errorCode: 'CONTEXT_NOT_FOUND',
          contexts: [],
          senses: [],
        }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        count === 1
          ? { ...common, status: 'PENDING', contexts: [], senses: [] }
          : {
              ...common,
              status: 'READY',
              lemma: 'vivir',
              senses: [{ partOfSpeech: 'verb', definition: 'to live' }],
              contexts: [
                {
                  number: 1,
                  sourceReference: 'src_spanish',
                  sourceKind: 'YOUTUBE_VIDEO',
                  sourceDisplayName: 'Easy Spanish transcript',
                  ordinal: 1,
                  displayText: 'Quiero vivir aquí.',
                  startMs: 15_000,
                  youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=15s',
                },
              ],
            },
      ),
    });
  });

  await page.goto('/app/lookup');
  const term = page.getByLabel('Term to look up');
  const submitLookup = page.getByRole('button', { name: /Look up/u });

  await term.fill('unknown');
  await submitLookup.click();
  await expect(page.getByText('No definition found')).toBeVisible();

  await term.fill('aislado');
  await submitLookup.click();
  await expect(page.getByText('No matching contexts')).toBeVisible();

  await term.fill('vivir');
  await submitLookup.click();
  await expect(page.locator('.lookup-spinner')).toHaveText('Q');
  await expect(page.getByText('Lookup in progress')).toBeVisible();
  await expect(page.getByText('Results for “vivir”')).toBeVisible();
  await expect(page.getByText('Quiero vivir aquí.')).toBeVisible();
  await expect(page.locator('.context-copy mark')).toHaveText('vivir');
  const videoLink = page.getByRole('link', {
    name: /Open Easy Spanish transcript at Video · 00:00:15 on YouTube/u,
  });
  await expect(videoLink).toHaveAttribute(
    'href',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=15s',
  );
  await expect(videoLink.locator('img')).toHaveAttribute(
    'src',
    'https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg',
  );
  await expect(polls.get('lookup-vivir')).toBe(2);
});

test('card management preserves versioned edits and confirms permanent deletion', async ({
  page,
}) => {
  await mockAuthentication(page, true);
  const deckReference = 'AAAAAAAAAAAAAAAAAAAAAA';
  let cards = [
    {
      reference: 'BBBBBBBBBBBBBBBBBBBBBB',
      deckReference,
      deckName: 'Vocabulary',
      noteType: 'BASIC',
      term: 'vivir',
      definition: 'to live',
      exampleText: 'Ella vive aquí.',
      version: 3,
      updatedAt: new Date().toISOString(),
    },
  ];

  await page.route('**/api/v1/lookups/decks', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ reference: deckReference, name: 'Vocabulary' }]),
    });
  });
  await page.route('**/api/v1/cards?*', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: cards,
        page: 0,
        pageSize: 50,
        totalItems: cards.length,
        totalPages: cards.length ? 1 : 0,
      }),
    });
  });
  await page.route('**/api/v1/cards/BBBBBBBBBBBBBBBBBBBBBB', async (route: Route) => {
    if (route.request().method() === 'PATCH') {
      const body = route.request().postDataJSON() as {
        term: string;
        definition: string;
        version: number;
      };
      expect(body).toMatchObject({
        term: 'vivir bien',
        definition: 'to live well',
        version: 3,
      });
      cards = [{ ...cards[0], ...body, version: 4 }];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(cards[0]),
      });
      return;
    }
    cards = [];
    await route.fulfill({ status: 204 });
  });

  await page.goto('/app/cards');
  await page.getByRole('button', { name: /vivir to live Vocabulary/u }).click();
  await page.getByLabel('Term').fill('vivir bien');
  await page.getByLabel('Definition').fill('to live well');
  await page.getByRole('button', { name: 'Save card' }).click();
  await expect(page.getByRole('button', { name: /vivir bien to live well/u })).toBeVisible();

  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Delete' }).click();
  await expect(page.getByText('No cards yet')).toBeVisible();

  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});

test('source management keeps filters in the URL and resumes source details', async ({ page }) => {
  await mockAuthentication(page, true);
  await page.goto('/app/sources?status=READY&kind=YOUTUBE_VIDEO');

  await expect(page.getByRole('heading', { name: 'Sources', exact: true })).toBeVisible();
  await expect(page.getByLabel('Status')).toHaveValue('READY');
  await expect(page.getByLabel('Type')).toHaveValue('YOUTUBE_VIDEO');
  await expect(page.getByRole('link', { name: 'Easy Spanish transcript' }).first()).toBeVisible();

  await page.getByRole('link', { name: 'Easy Spanish transcript' }).first().click();
  await expect(page).toHaveURL(/\/app\/sources\/src_spanish$/u);
  await expect(page.getByRole('heading', { name: 'Easy Spanish transcript' })).toBeVisible();
  await expect(page.getByText('ready', { exact: true })).toBeVisible();

  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});

test('pasted-text intake navigates to the accepted durable source URL', async ({ page }) => {
  await mockAuthentication(page, true);
  await page.goto('/app/sources/new');

  await page.getByLabel('Source text').fill('Texto añadido desde la biblioteca.');
  await page.getByRole('button', { name: 'Add pasted text' }).click();

  await expect(page).toHaveURL(/\/app\/sources\/src_playwright$/u);
  await expect(page.getByRole('heading', { name: 'Browser text' })).toBeVisible();
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
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(sourcePage),
      });
      return;
    }
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

  await page.route('**/api/v1/sources?*', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(sourcePage),
    });
  });

  await page.route('**/api/v1/sources/src_spanish', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(sourceDetails),
    });
  });

  await page.route('**/api/v1/sources/src_playwright', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ...sourceDetails,
        reference: 'src_playwright',
        kind: 'PASTED_TEXT',
        displayName: 'Browser text',
        origin: 'WEBSITE',
        youtube: null,
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

const sourcePage = {
  items: [
    {
      reference: 'src_spanish',
      kind: 'YOUTUBE_VIDEO',
      status: 'READY',
      displayName: 'Easy Spanish transcript',
      language: 'es',
      characterCount: 12_345,
      passageCount: 42,
      retryable: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
    },
  ],
  page: 0,
  pageSize: 20,
  totalItems: 1,
  totalPages: 1,
};

const sourceDetails = {
  ...sourcePage.items[0],
  origin: 'WEBSITE',
  youtube: {
    canonicalUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    title: 'Easy Spanish',
    channelName: 'Easy Languages',
    durationMs: 120_000,
    captionKind: 'MANUAL',
  },
};
