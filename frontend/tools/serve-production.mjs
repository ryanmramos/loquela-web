import { createReadStream, readFileSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize, resolve } from 'node:path';
import { randomBytes } from 'node:crypto';

const root = resolve('dist/loquela-web/browser');
const port = Number(process.env['LOQUELA_PRODUCTION_PORT'] ?? 4200);
const indexTemplate = readFileSync(join(root, 'index.html'), 'utf8');
const runtimeConfigTemplate = readFileSync(join(root, 'config.json'), 'utf8');
const CADDY_NONCE_TEMPLATE = '{{placeholder "http.request.uuid"}}';
const PRIMEUI_LICENSE_TEMPLATE = '{{printf "%q" (placeholder "env.PRIMEUI_LICENSE_KEY")}}';
const primeUiLicenseKey = process.env['PRIMEUI_LICENSE_KEY'];

const mimeTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.map', 'application/json; charset=utf-8'],
  ['.png', 'image/png'],
  ['.svg', 'image/svg+xml'],
  ['.webp', 'image/webp'],
  ['.woff2', 'font/woff2'],
]);

function securityHeaders(nonce) {
  return {
    'Content-Security-Policy': [
      "default-src 'self'",
      `script-src 'self' 'nonce-${nonce}'`,
      `style-src 'self' 'nonce-${nonce}'`,
      "connect-src 'self'",
      "font-src 'self'",
      "img-src 'self' data: https://i.ytimg.com",
      'frame-src https://www.youtube-nocookie.com',
      "object-src 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "frame-ancestors 'none'",
      'trusted-types angular angular#bundler',
      "require-trusted-types-for 'script'",
    ].join('; '),
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
  };
}

function sendIndex(response) {
  const nonce = randomBytes(18).toString('base64');
  const html = indexTemplate
    .replaceAll(CADDY_NONCE_TEMPLATE, nonce)
    .replace('<app-root', `<app-root ngCspNonce="${nonce}"`)
    .replaceAll('<script ', `<script nonce="${nonce}" `)
    .replaceAll('<style>', `<style nonce="${nonce}">`)
    // Angular's async stylesheet optimization uses an inline onload handler. The production
    // fixture deliberately makes it a normal stylesheet so script attributes remain denied.
    .replace(
      /<link rel="stylesheet" href="([^"]+)" media="print" onload="this\.media='all'">/u,
      '<link rel="stylesheet" href="$1">',
    );
  response.writeHead(200, {
    ...securityHeaders(nonce),
    'Cache-Control': 'no-cache',
    'Content-Type': 'text/html; charset=utf-8',
  });
  response.end(html);
}

function sendRuntimeConfig(response) {
  const config = primeUiLicenseKey
    ? runtimeConfigTemplate.replace(PRIMEUI_LICENSE_TEMPLATE, JSON.stringify(primeUiLicenseKey))
    : runtimeConfigTemplate.replace(`,\n  "primeUiLicenseKey": ${PRIMEUI_LICENSE_TEMPLATE}`, '');
  response.writeHead(200, {
    'Cache-Control': 'no-store',
    'Content-Type': 'application/json; charset=utf-8',
  });
  response.end(config);
}

createServer((request, response) => {
  const url = new URL(request.url ?? '/', `http://${request.headers.host ?? '127.0.0.1'}`);
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/oauth2/')) {
    response.writeHead(502, { 'Cache-Control': 'no-store', 'Content-Type': 'application/json' });
    response.end('{"error":"Production fixture API request was not mocked."}');
    return;
  }

  if (url.pathname === '/config.json') {
    sendRuntimeConfig(response);
    return;
  }

  const decodedPath = decodeURIComponent(url.pathname);
  const candidate = normalize(join(root, decodedPath));
  if (!candidate.startsWith(root)) {
    response.writeHead(400);
    response.end();
    return;
  }

  try {
    if (statSync(candidate).isFile()) {
      const extension = extname(candidate);
      response.writeHead(200, {
        ...securityHeaders(randomBytes(18).toString('base64')),
        'Cache-Control':
          extension === '.json'
            ? 'no-store'
            : /-[A-Za-z0-9_-]{8,}\./u.test(candidate)
              ? 'public, max-age=31536000, immutable'
              : 'no-cache',
        'Content-Type': mimeTypes.get(extension) ?? 'application/octet-stream',
      });
      createReadStream(candidate).pipe(response);
      return;
    }
  } catch {
    // Angular routes intentionally fall through to index.html.
  }
  sendIndex(response);
}).listen(port, '127.0.0.1', () => {
  process.stdout.write(`Loquela production fixture listening on http://127.0.0.1:${port}\n`);
});
