import { expect, test } from '@playwright/test';

test('compiled SPA enforces the production security-header contract', async ({ page }) => {
  const policyViolations: string[] = [];
  await page.exposeFunction('recordPolicyViolation', (directive: string) => {
    policyViolations.push(directive);
  });
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      const record = window as typeof window & {
        recordPolicyViolation: (directive: string) => void;
      };
      void record.recordPolicyViolation(event.effectiveDirective);
    });
  });

  const response = await page.goto('/error');
  await expect(page.getByRole('heading', { name: 'Something went wrong' })).toBeVisible();

  const headers = response?.headers() ?? {};
  expect(headers['content-security-policy']).toContain("require-trusted-types-for 'script'");
  expect(headers['content-security-policy']).toContain('trusted-types angular angular#bundler');
  expect(headers['content-security-policy']).toContain("frame-ancestors 'none'");
  expect(headers['x-frame-options']).toBe('DENY');
  expect(headers['x-content-type-options']).toBe('nosniff');
  expect(headers['referrer-policy']).toBe('strict-origin-when-cross-origin');
  expect(policyViolations).toEqual([]);
});

test('runtime config is never cached and hashed assets are immutable', async ({ request }) => {
  const config = await request.get('/config.json');
  expect(config.headers()['cache-control']).toBe('no-store');
  await expect(config.json()).resolves.toMatchObject({
    environmentName: 'production',
    localLoginEnabled: false,
  });

  const index = await request.get('/');
  const html = await index.text();
  const assetPath = html.match(/(?:src|href)="([^"]+-[A-Za-z0-9_-]{8,}\.(?:js|css))"/u)?.[1];
  expect(assetPath).toBeTruthy();
  const asset = await request.get(assetPath ?? '');
  expect(asset.headers()['cache-control']).toBe('public, max-age=31536000, immutable');
});
