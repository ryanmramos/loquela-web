import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { findArchitectureViolations } from './architecture-rules.mjs';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const violations = await findArchitectureViolations(frontendRoot);

if (violations.length > 0) {
  console.error(['Frontend architecture violations:', ...violations].join('\n- '));
  process.exitCode = 1;
} else {
  console.log('Frontend architecture boundaries pass.');
}
