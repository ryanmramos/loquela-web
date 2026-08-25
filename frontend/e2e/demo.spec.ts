import { expect, test } from '@playwright/test';

declare global {
  interface Window {
    recordPolicyViolation: (directive: string) => void;
  }
}

import { mockAuthenticatedE2eRuntimeConfig } from './runtime-config';

const capabilities = {
  enabled: true,
  ingestionEnabled: true,
  timedTranscriptEnabled: true,
  supportedLanguagePairs: ['es:en', 'en:es'],
  limits: {
    pdfBytes: 5_000_000,
    pdfPages: 20,
    pdfCharacters: 100_000,
    timedTranscriptBytes: 1_000_000,
    timedTranscriptCues: 2_000,
    timedTranscriptCharacters: 100_000,
    pastedTextCharacters: 10_000,
    totalIndexedCharacters: 100_000,
    uniqueLookups: 20,
    cards: 20,
  },
};

const workspace = {
  createdAt: '2026-08-25T00:00:00Z',
  inactivityExpiresAt: '2026-08-25T01:00:00Z',
  absoluteExpiresAt: '2026-08-25T02:00:00Z',
  sourceLanguage: 'es',
  targetLanguage: 'en',
  corpusRevision: 0,
  sources: [],
  indexedCharacters: 0,
  cards: [],
  deckRevision: 0,
  exportReady: false,
};

test('demo language dropdown keeps its PrimeNG overlay styles under the production CSP', async ({
  page,
}) => {
  const policyViolations: string[] = [];
  await page.exposeFunction('recordPolicyViolation', (directive: string) => {
    policyViolations.push(directive);
  });
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      void window.recordPolicyViolation(event.effectiveDirective);
    });
  });
  await mockAuthenticatedE2eRuntimeConfig(page);
  await page.route('**/api/v1/session', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({ status: 401, title: 'Unauthorized' }),
    }),
  );
  await page.route('**/api/v1/demo/capabilities', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(capabilities) }),
  );
  await page.route('**/api/v1/demo/workspace', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(workspace) }),
  );

  await page.goto('/demo');
  await page.getByRole('button', { name: 'Start demo' }).click();

  const sourceSelect = page.locator('p-select').first();
  await expect(sourceSelect.locator('.p-select-label')).toHaveText('Spanish');
  await sourceSelect.click();
  const overlay = page.locator('.p-select-overlay');
  await expect(overlay).toBeVisible();

  const [selectBox, overlayBox, overlayPosition] = await Promise.all([
    sourceSelect.boundingBox(),
    overlay.boundingBox(),
    overlay.evaluate((element) => getComputedStyle(element).position),
  ]);
  expect(selectBox).not.toBeNull();
  expect(overlayBox).not.toBeNull();
  expect(overlayPosition).not.toBe('static');
  expect(overlayBox!.x).toBeGreaterThanOrEqual(selectBox!.x - 1);
  expect(overlayBox!.y).toBeGreaterThanOrEqual(selectBox!.y);
  expect(policyViolations).toEqual([]);
});
