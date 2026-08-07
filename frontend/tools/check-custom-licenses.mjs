import { execFileSync } from 'node:child_process';

const approvedPrimeUiPackages = new Set([
  '@primeicons/angular@8.0.0',
  '@primeicons/core@8.0.0',
  '@primeui/license-manager@1.0.0',
  '@primeuix/motion@1.0.0',
  '@primeuix/styled@1.0.0',
  '@primeuix/styles@3.0.0',
  '@primeuix/themes@3.0.0',
  '@primeuix/utils@0.8.0',
  'primeng@22.0.0',
]);

const report = JSON.parse(
  execFileSync(
    process.execPath,
    [
      'node_modules/license-checker-rseidelsohn/bin/license-checker-rseidelsohn.js',
      '--production',
      '--json',
    ],
    { encoding: 'utf8' },
  ),
);
const customPackages = Object.entries(report)
  .filter(([, metadata]) => String(metadata.licenses).startsWith('Custom:'))
  .map(([name]) => name)
  .sort();
const unexpected = customPackages.filter((name) => !approvedPrimeUiPackages.has(name));
const missing = [...approvedPrimeUiPackages].filter((name) => !customPackages.includes(name));

if (unexpected.length || missing.length) {
  throw new Error(
    `Custom-license review drifted. Unexpected: ${unexpected.join(', ') || 'none'}. Missing: ${missing.join(', ') || 'none'}.`,
  );
}

process.stdout.write('Reviewed PrimeUI custom-license package set is unchanged.\n');
