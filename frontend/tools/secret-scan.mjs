import { spawnSync } from 'node:child_process';
import { lstatSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const SECRET_SCANNER_VERSION = 'loquela-secret-scan/1';

const rules = [
  ['private-key', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/u],
  ['aws-access-key', /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/u],
  ['github-token', /\bgh[opsu]_[A-Za-z0-9]{30,}\b/u],
  ['google-api-key', /\bAIza[0-9A-Za-z_-]{35}\b/u],
  ['stripe-live-key', /\b(?:sk|rk)_live_[0-9A-Za-z]{20,}\b/u],
  ['slack-token', /\bxox[baprs]-[0-9A-Za-z-]{20,}\b/u],
];
const repositoryRoot = fileURLToPath(new URL('../..', import.meta.url));

export function findSecretRuleIds(text) {
  return rules.filter(([, pattern]) => pattern.test(text)).map(([id]) => id);
}

function scanWorkingTree() {
  const files = spawnSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {
    cwd: repositoryRoot,
    encoding: 'utf8',
  });
  if (files.status !== 0) throw new Error('Tracked and untracked content could not be listed.');

  const findings = new Set();
  for (const file of files.stdout.split('\0').filter(Boolean)) {
    if (file === 'frontend/package-lock.json') continue;
    const path = join(repositoryRoot, file);
    try {
      if (lstatSync(path).isSymbolicLink()) continue;
      const content = readFileSync(path);
      if (content.length > 5 * 1024 * 1024 || content.includes(0)) continue;
      findSecretRuleIds(content.toString('utf8')).forEach((finding) => findings.add(finding));
    } catch (error) {
      // A file removed between listing and reading does not weaken committed-history coverage.
      if (error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT')
        continue;
      throw new Error(`Repository content could not be read: ${file}`, { cause: error });
    }
  }
  return findings;
}

function scanGitHistory() {
  const result = spawnSync(
    'git',
    [
      'log',
      '--all',
      '--format=',
      '--no-ext-diff',
      '--patch',
      '--',
      '.',
      ':(exclude)frontend/package-lock.json',
    ],
    { cwd: repositoryRoot, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 },
  );
  if (result.status !== 0) throw new Error('Git history could not be read for secret scanning.');

  return new Set(findSecretRuleIds(result.stdout));
}

function scanRepository() {
  const findings = new Set([...scanWorkingTree(), ...scanGitHistory()]);
  if (findings.size > 0) {
    // Do not print matching content: scanner output itself must not become a leakage path.
    throw new Error(`Secret scan failed for rule categories: ${[...findings].join(', ')}`);
  }
  process.stdout.write(`${SECRET_SCANNER_VERSION}: tracked content and Git history pass.\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) scanRepository();
