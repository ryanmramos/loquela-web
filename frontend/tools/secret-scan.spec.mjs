import assert from 'node:assert/strict';
import test from 'node:test';

import { findSecretRuleIds } from './secret-scan.mjs';

test('detects credential formats without treating documented placeholders as secrets', () => {
  assert.deepEqual(findSecretRuleIds('TWILIO_AUTH_TOKEN=<secret>\nAPI_KEY=test-secret-key'), []);
  assert.deepEqual(findSecretRuleIds(`token=${'ghp_'}${'123456789012345678901234567890123456'}`), [
    'github-token',
  ]);
  assert.deepEqual(findSecretRuleIds(`-----BEGIN ${'PRIVATE'} KEY-----`), ['private-key']);
});
