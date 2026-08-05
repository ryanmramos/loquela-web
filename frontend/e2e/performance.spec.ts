import { expect, test } from '@playwright/test';

test('login excludes authenticated feature chunks and routes load them lazily', async ({
  page,
}) => {
  let authenticated = false;
  const scripts = new Set<string>();
  page.on('response', (response) => {
    const path = new URL(response.url()).pathname;
    if (path.endsWith('.js')) scripts.add(path);
  });
  await page.route('**/api/v1/session', (route) =>
    route.fulfill({
      status: authenticated ? 200 : 401,
      contentType: authenticated ? 'application/json' : 'application/problem+json',
      body: JSON.stringify(
        authenticated
          ? {
              userId: '529b7902-040a-4a40-8905-aa4ee06d2413',
              displayName: 'Local Learner',
              accountState: 'ACTIVE',
              linkedAccounts: { google: true, whatsapp: false },
            }
          : { status: 401, code: 'AUTHENTICATION_REQUIRED', fieldErrors: [] },
      ),
    }),
  );
  await page.route('**/api/v1/auth/local-login', (route) => {
    authenticated = true;
    return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });
  await page.route('**/api/v1/dashboard', (route) =>
    route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{}' }),
  );
  const emptyPage = { items: [], page: 0, pageSize: 20, totalItems: 0, totalPages: 0 };
  await page.route('**/api/v1/decks**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(emptyPage),
    }),
  );
  await page.route('**/api/v1/exports**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(emptyPage),
    }),
  );

  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Local Learner' })).toBeVisible();
  const loginScripts = new Set(scripts);
  await page.getByRole('button', { name: 'Local Learner' }).click();
  await page.goto('/app/decks');
  await expect(page.getByRole('heading', { name: 'Decks', exact: true })).toBeVisible();

  const authenticatedOnlyScripts = [...scripts].filter((script) => !loginScripts.has(script));
  expect(authenticatedOnlyScripts.length).toBeGreaterThan(0);
});

test('three-run mobile lab medians stay within the Step 16 budgets', async ({
  page,
  browserName,
}) => {
  test.skip(browserName !== 'chromium', 'Chromium CDP supplies the repeatable lab profile.');

  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  await cdp.send('Network.emulateNetworkConditions', {
    offline: false,
    latency: 150,
    downloadThroughput: (1_600 * 1024) / 8,
    uploadThroughput: (750 * 1024) / 8,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => {
    const measurements = { lcp: 0, cls: 0, tbt: 0 };
    (window as typeof window & { __step16Vitals: typeof measurements }).__step16Vitals =
      measurements;
    new PerformanceObserver((list) => {
      measurements.lcp = list.getEntries().at(-1)?.startTime ?? measurements.lcp;
    }).observe({ type: 'largest-contentful-paint', buffered: true });
    new PerformanceObserver((list) => {
      for (const entry of list.getEntries() as (PerformanceEntry & {
        hadRecentInput?: boolean;
        value?: number;
      })[]) {
        if (!entry.hadRecentInput) measurements.cls += entry.value ?? 0;
      }
    }).observe({ type: 'layout-shift', buffered: true });
    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) measurements.tbt += Math.max(0, entry.duration - 50);
    }).observe({ type: 'longtask', buffered: true });
  });

  const runs: { lcp: number; cls: number; tbt: number }[] = [];
  for (let run = 0; run < 3; run += 1) {
    await page.goto('/error');
    await expect(page.getByRole('heading', { name: 'Something went wrong' })).toBeVisible();
    await page.waitForTimeout(250);
    runs.push(
      await page.evaluate(
        () =>
          (window as typeof window & { __step16Vitals: { lcp: number; cls: number; tbt: number } })
            .__step16Vitals,
      ),
    );
  }

  const median = (values: number[]) => [...values].sort((a, b) => a - b)[1] ?? Infinity;
  expect(median(runs.map(({ lcp }) => lcp))).toBeLessThanOrEqual(2_500);
  expect(median(runs.map(({ cls }) => cls))).toBeLessThanOrEqual(0.1);
  expect(median(runs.map(({ tbt }) => tbt))).toBeLessThanOrEqual(200);

  const interactionDuration = await page.getByRole('link', { name: 'Return to Loquela' }).evaluate(
    (link) =>
      new Promise<number>((resolve) => {
        const startedAt = performance.now();
        (link as HTMLAnchorElement).focus();
        requestAnimationFrame(() =>
          requestAnimationFrame(() => resolve(performance.now() - startedAt)),
        );
      }),
  );
  await expect(page.getByRole('link', { name: 'Return to Loquela' })).toBeFocused();
  expect(interactionDuration).toBeLessThanOrEqual(200);
});
