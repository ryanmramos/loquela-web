import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import assert from 'node:assert/strict';

import { prepareCaddyArtifact } from './prepare-caddy-artifact.mjs';

test('prepares an immutable production config and nonce-bearing Angular index', () => {
  const root = mkdtempSync(join(tmpdir(), 'loquela-caddy-'));
  mkdirSync(root, { recursive: true });
  writeFileSync(
    join(root, 'index.html'),
    '<style>body{}</style><link rel="stylesheet" href="styles-ABCDEFGH.css" media="print" onload="this.media=\'all\'"><app-root></app-root><script src="main-ABCDEFGH.js"></script>',
  );

  prepareCaddyArtifact(root, 'abc123');
  prepareCaddyArtifact(root, 'abc123');

  const index = readFileSync(join(root, 'index.html'), 'utf8');
  const configTemplate = readFileSync(join(root, 'config.json'), 'utf8');
  const config = JSON.parse(
    configTemplate.replace(
      '{{printf "%q" (placeholder "env.PRIMEUI_LICENSE_KEY")}}',
      '"verification-license"',
    ),
  );
  assert.match(index, /ngCspNonce=/u);
  assert.match(index, /nonce=/u);
  assert.doesNotMatch(index, /onload=/u);
  assert.equal(index.match(/ngCspNonce=/gu)?.length, 1);
  assert.equal(config.releaseId, 'abc123');
  assert.equal(config.localLoginEnabled, false);
  assert.equal(config.telemetryEnabled, false);
  assert.match(
    configTemplate,
    /"primeUiLicenseKey": \{\{printf "%q" \(placeholder "env\.PRIMEUI_LICENSE_KEY"\)\}\}/u,
  );
});

test('rejects a release identifier that could corrupt runtime JSON or HTML', () => {
  const root = mkdtempSync(join(tmpdir(), 'loquela-caddy-'));
  assert.throws(() => prepareCaddyArtifact(root, 'bad value"'), /LOQUELA_RELEASE_ID/u);
});
