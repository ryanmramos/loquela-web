import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

const browserRoot = resolve('dist/loquela-web/browser');
const index = readFileSync(join(browserRoot, 'index.html'), 'utf8');
const initialFiles = new Set(
  [...index.matchAll(/(?:src|href)="([^"]+\.(?:js|css))"/gu)].map((match) => match[1]),
);
const initialBytes = [...initialFiles].reduce(
  (total, file) => total + statSync(join(browserRoot, file)).size,
  0,
);
const lazyScripts = readdirSync(browserRoot)
  .filter((file) => file.endsWith('.js') && !initialFiles.has(file))
  .map((file) => ({ file, bytes: statSync(join(browserRoot, file)).size }));
const largestLazy = lazyScripts.sort((left, right) => right.bytes - left.bytes)[0];

const failures = [];
if (initialBytes > 450_000) failures.push(`initial output is ${initialBytes} bytes (limit 450000)`);
if (largestLazy && largestLazy.bytes > 75_000) {
  failures.push(`${largestLazy.file} is ${largestLazy.bytes} bytes (lazy limit 75000)`);
}

if (failures.length) {
  throw new Error(`Bundle budget failure:\n${failures.join('\n')}`);
}

if (initialBytes > 400_000) {
  process.stderr.write(
    `Bundle warning: initial output is ${initialBytes} bytes (warning 400000)\n`,
  );
}
process.stdout.write(
  `Bundle budgets pass: initial=${initialBytes} bytes, largestLazy=${largestLazy?.bytes ?? 0} bytes.\n`,
);
