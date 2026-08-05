import { spawn } from 'node:child_process';

import { chromium } from 'playwright';

const appPort = 4202;
const server = spawn(process.execPath, ['tools/serve-production.mjs'], {
  env: { ...process.env, LOQUELA_PRODUCTION_PORT: String(appPort) },
  stdio: 'ignore',
});

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function waitForServer() {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${appPort}/config.json`);
      if (response.ok) return;
    } catch {
      // The production fixture can take a moment to bind its localhost port.
    }
    await delay(250);
  }
  throw new Error('The production fixture did not become ready for the Edge smoke test.');
}

let browser;
try {
  await waitForServer();
  browser = await chromium.launch({ channel: 'msedge' });
  const page = await browser.newPage({ viewport: { width: 320, height: 720 } });
  const violations = [];
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      window.__step16PolicyViolations = [
        ...(window.__step16PolicyViolations ?? []),
        event.effectiveDirective,
      ];
    });
  });
  const response = await page.goto(`http://127.0.0.1:${appPort}/error`);
  await page.getByRole('heading', { name: 'Something went wrong' }).waitFor();
  const evidence = await page.evaluate(() => ({
    title: document.title,
    language: document.documentElement.lang,
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
    policyViolations: window.__step16PolicyViolations ?? [],
  }));
  const policy = response?.headers()['content-security-policy'] ?? '';
  if (
    evidence.title !== 'Something went wrong · Loquela' ||
    evidence.language !== 'en' ||
    evidence.scrollWidth > evidence.clientWidth ||
    evidence.policyViolations.length ||
    !policy.includes("require-trusted-types-for 'script'")
  ) {
    throw new Error(`Edge smoke failed: ${JSON.stringify(evidence)}`);
  }
  process.stdout.write(`Edge smoke passes: ${JSON.stringify(evidence)}\n`);
} finally {
  await browser?.close();
  server.kill('SIGTERM');
}
