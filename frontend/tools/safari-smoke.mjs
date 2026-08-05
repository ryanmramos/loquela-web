import { spawn } from 'node:child_process';

const driverPort = 4444;
const appPort = 4201;
const driver = spawn('/usr/bin/safaridriver', ['-p', String(driverPort)], { stdio: 'ignore' });
const server = spawn(process.execPath, ['tools/serve-production.mjs'], {
  env: { ...process.env, LOQUELA_PRODUCTION_PORT: String(appPort) },
  stdio: 'ignore',
});

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function webdriver(path, method = 'GET', body) {
  const response = await fetch(`http://127.0.0.1:${driverPort}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const result = await response.json();
  if (!response.ok || result.value?.error) {
    throw new Error(`Safari WebDriver request failed: ${result.value?.message ?? response.status}`);
  }
  return result.value;
}

async function waitForDriver() {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      await webdriver('/status');
      return;
    } catch {
      await delay(250);
    }
  }
  throw new Error('SafariDriver did not become ready. Enable Remote Automation in Safari.');
}

async function waitForApplication(sessionId) {
  // Safari may report navigation complete before Angular has rendered the lazy route.
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const ready = await webdriver(`/session/${sessionId}/execute/sync`, 'POST', {
      script: `return document.title === 'Design foundation · Loquela'
        && document.querySelector('main h1')?.textContent?.trim() === 'Your language workspace';`,
      args: [],
    });
    if (ready) return;
    await delay(250);
  }
  throw new Error('Safari smoke timed out waiting for the Angular route to become ready.');
}

let sessionId;
try {
  await waitForDriver();
  const session = await webdriver('/session', 'POST', {
    capabilities: { alwaysMatch: { browserName: 'safari' } },
  });
  sessionId = session.sessionId;
  await webdriver(`/session/${sessionId}/window/rect`, 'POST', { width: 320, height: 720 });
  await webdriver(`/session/${sessionId}/url`, 'POST', {
    url: `http://127.0.0.1:${appPort}/foundation`,
  });
  await waitForApplication(sessionId);
  const evidence = await webdriver(`/session/${sessionId}/execute/sync`, 'POST', {
    script: `return {
      title: document.title,
      heading: document.querySelector('main h1')?.textContent?.trim(),
      language: document.documentElement.lang,
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth
    };`,
    args: [],
  });
  if (
    evidence.title !== 'Design foundation · Loquela' ||
    evidence.heading !== 'Your language workspace' ||
    evidence.language !== 'en' ||
    evidence.scrollWidth > evidence.clientWidth
  ) {
    throw new Error(`Safari smoke failed: ${JSON.stringify(evidence)}`);
  }
  process.stdout.write(`Safari smoke passes: ${JSON.stringify(evidence)}\n`);
} finally {
  if (sessionId) await webdriver(`/session/${sessionId}`, 'DELETE').catch(() => undefined);
  driver.kill('SIGTERM');
  server.kill('SIGTERM');
}
