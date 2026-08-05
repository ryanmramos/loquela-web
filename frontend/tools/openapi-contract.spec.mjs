import assert from 'node:assert/strict';
import path from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

import { readBrowserOpenApi, validateBrowserOpenApi } from './openapi-contract.mjs';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const contractPath = path.resolve(frontendRoot, '..', 'openapi', 'browser-v1.json');
const validContract = await readBrowserOpenApi(contractPath);

test('accepts the committed browser contract', () => {
  assert.deepEqual(validateBrowserOpenApi(validContract), []);
});

test('rejects an incompatible session response', () => {
  const incompatible = structuredClone(validContract);
  delete incompatible.components.schemas.BrowserSessionDto.properties.userId;

  assert.ok(
    validateBrowserOpenApi(incompatible).includes(
      'BrowserSessionDto.userId must be present and required.',
    ),
  );
});

test('rejects absolute API origins and persistence entities', () => {
  const unsafe = structuredClone(validContract);
  unsafe.servers = [{ url: 'https://api.example.com' }];
  unsafe.components.schemas.AppUserEntity = { type: 'object' };

  const errors = validateBrowserOpenApi(unsafe);
  assert.ok(errors.some((error) => error.includes('same-origin')));
  assert.ok(errors.some((error) => error.includes('JPA schema')));
});

test('rejects activity contract drift', () => {
  const incompatible = structuredClone(validContract);
  delete incompatible.paths['/api/v1/activity/status-query'];

  assert.ok(
    validateBrowserOpenApi(incompatible).some((error) =>
      error.includes('POST /api/v1/activity/status-query'),
    ),
  );
});
