import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const frontendPort = 4317;
const backend = createServer((request, response) => {
  if (request.url === '/actuator/health') {
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ status: 'UP' }));
    return;
  }
  if (request.url === '/api/v1/session') {
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(
      JSON.stringify({
        userId: '529b7902-040a-4a40-8905-aa4ee06d2413',
        sourceLanguage: 'es',
        definitionLanguage: 'en',
        linkedAccounts: { google: true, whatsapp: false },
      }),
    );
    return;
  }

  response.writeHead(404);
  response.end();
});

await listen(backend);
const backendAddress = backend.address();
if (!backendAddress || typeof backendAddress === 'string') {
  throw new Error('Could not determine the proxy-check backend address.');
}

const angular = spawn(
  process.execPath,
  [
    path.join(frontendRoot, 'node_modules', '@angular', 'cli', 'bin', 'ng.js'),
    'serve',
    '--host',
    '127.0.0.1',
    '--port',
    String(frontendPort),
  ],
  {
    cwd: frontendRoot,
    env: {
      ...process.env,
      LOQUELA_BACKEND_URL: `http://127.0.0.1:${backendAddress.port}`,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  },
);

let angularOutput = '';
angular.stdout.on('data', (chunk) => {
  angularOutput += chunk.toString();
});
angular.stderr.on('data', (chunk) => {
  angularOutput += chunk.toString();
});

try {
  const response = await waitForResponse(`http://127.0.0.1:${frontendPort}/actuator/health`);
  const body = await response.json();

  if (!response.ok || body.status !== 'UP') {
    throw new Error('The proxied health endpoint returned an unexpected response.');
  }
  const sessionResponse = await fetch(`http://127.0.0.1:${frontendPort}/api/v1/session`);
  const session = await sessionResponse.json();
  if (!sessionResponse.ok || session.userId !== '529b7902-040a-4a40-8905-aa4ee06d2413') {
    throw new Error('The proxied browser session endpoint returned an unexpected response.');
  }
  console.log('Local Angular proxy reaches the backend health and browser session endpoints.');
} catch (error) {
  console.error(angularOutput);
  throw error;
} finally {
  angular.kill('SIGTERM');
  backend.close();
}

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
}

async function waitForResponse(url) {
  const deadline = Date.now() + 60_000;

  while (Date.now() < deadline) {
    if (angular.exitCode !== null) {
      throw new Error(`Angular exited before the proxy was ready (${angular.exitCode}).`);
    }

    try {
      return await fetch(url);
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
  }

  throw new Error('Timed out waiting for the Angular development proxy.');
}
