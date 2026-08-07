import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env['CI']),
  retries: process.env['CI'] ? 2 : 0,
  maxFailures: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? 'github' : 'list',
  use: {
    baseURL: 'http://127.0.0.1:4200',
    // Traces contain fulfilled runtime-config bodies. Do not publish the browser-delivered token.
    trace: process.env['PRIMEUI_LICENSE_KEY'] ? 'off' : 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    {
      name: 'firefox-smoke',
      testMatch: /(?:app|auth|security)\.spec\.ts/u,
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit-smoke',
      testMatch: /(?:app|auth|security)\.spec\.ts/u,
      use: { ...devices['Desktop Safari'] },
    },
  ],
  webServer: {
    command: 'node tools/serve-production.mjs',
    url: 'http://127.0.0.1:4200',
    reuseExistingServer: false,
    timeout: 30_000,
  },
});
