import { Page } from '@playwright/test';

const primeUiLicenseKey = process.env['PRIMEUI_LICENSE_KEY'];

export async function mockAuthenticatedE2eRuntimeConfig(page: Page): Promise<void> {
  await page.route('**/config.local.json', (route) => route.fulfill({ status: 404 }));
  await page.route('**/config.json', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: { 'Cache-Control': 'no-store' },
      body: JSON.stringify({
        apiBasePath: '/api/v1',
        googleLoginPath: '/api/v1/auth/google',
        localLoginEnabled: true,
        supportUrl: '/support',
        environmentName: 'test',
        telemetryEnabled: false,
        releaseId: 'e2e',
        ...(primeUiLicenseKey ? { primeUiLicenseKey } : {}),
      }),
    }),
  );
}
