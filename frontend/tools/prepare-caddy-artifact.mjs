import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const RELEASE_PATTERN = /^[A-Za-z0-9._-]{1,128}$/u;
const NONCE_TEMPLATE = '{{placeholder "http.request.uuid"}}';
const PRIMEUI_LICENSE_MARKER = '__PRIMEUI_LICENSE_KEY__';
const PRIMEUI_LICENSE_TEMPLATE = '{{printf "%q" (placeholder "env.PRIMEUI_LICENSE_KEY")}}';

export function prepareCaddyArtifact(root, releaseId) {
  if (!RELEASE_PATTERN.test(releaseId)) {
    throw new Error(
      'LOQUELA_RELEASE_ID must contain only letters, numbers, dot, underscore, or hyphen.',
    );
  }

  const indexPath = join(root, 'index.html');
  const sourceIndex = readFileSync(indexPath, 'utf8');
  const index = sourceIndex.includes(NONCE_TEMPLATE)
    ? sourceIndex
    : sourceIndex
        .replace('<app-root', `<app-root ngCspNonce='${NONCE_TEMPLATE}'`)
        .replaceAll('<script ', `<script nonce='${NONCE_TEMPLATE}' `)
        .replaceAll('<style>', `<style nonce='${NONCE_TEMPLATE}'>`)
        .replace(
          /<link rel="stylesheet" href="([^"]+)" media="print" onload="this\.media='all'">/u,
          '<link rel="stylesheet" href="$1">',
        );

  if (!index.includes('ngCspNonce=') || !index.includes('nonce=')) {
    throw new Error(
      'The production index did not contain the expected Angular CSP insertion points.',
    );
  }
  if (index.includes(' onload=')) {
    throw new Error('Inline event handlers are forbidden in the Caddy artifact.');
  }

  writeFileSync(indexPath, index);
  writeFileSync(
    join(root, 'config.json'),
    `${JSON.stringify(
      {
        apiBasePath: '/api/v1',
        googleLoginPath: '/api/v1/auth/google',
        localLoginEnabled: false,
        supportUrl: '/support',
        environmentName: 'production',
        telemetryEnabled: false,
        releaseId,
        primeUiLicenseKey: PRIMEUI_LICENSE_MARKER,
      },
      null,
      2,
    ).replace(`"${PRIMEUI_LICENSE_MARKER}"`, PRIMEUI_LICENSE_TEMPLATE)}\n`,
  );
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (invokedPath === fileURLToPath(import.meta.url)) {
  prepareCaddyArtifact(
    resolve('dist/loquela-web/browser'),
    process.env['LOQUELA_RELEASE_ID'] ?? 'development',
  );
}
