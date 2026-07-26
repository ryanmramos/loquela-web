import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { readBrowserOpenApi, validateBrowserOpenApi } from './openapi-contract.mjs';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const contractPath = path.resolve(frontendRoot, '..', 'openapi', 'browser-v1.json');
const contract = await readBrowserOpenApi(contractPath);
const errors = validateBrowserOpenApi(contract);

if (errors.length > 0) {
  console.error(['Browser OpenAPI contract is incompatible:', ...errors].join('\n- '));
  process.exitCode = 1;
} else {
  const generatorPath = path.join(
    frontendRoot,
    'node_modules',
    'ng-openapi-gen',
    'lib',
    'index.js',
  );
  const result = spawnSync(process.execPath, [generatorPath], {
    cwd: frontendRoot,
    stdio: 'inherit',
  });
  process.exitCode = result.status ?? 1;
}
