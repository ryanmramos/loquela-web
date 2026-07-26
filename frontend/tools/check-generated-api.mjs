import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const generatedRoot = path.join(frontendRoot, 'src', 'app', 'api', 'generated');
const before = await snapshot(generatedRoot);
const generation = spawnSync(
  process.execPath,
  [path.join(frontendRoot, 'tools', 'generate-api.mjs')],
  {
    cwd: frontendRoot,
    stdio: 'inherit',
  },
);

if (generation.status !== 0) {
  process.exitCode = generation.status ?? 1;
} else {
  const after = await snapshot(generatedRoot);
  const changed = changedFiles(before, after);

  if (changed.length > 0) {
    console.error(
      [
        'Generated API drift detected. Review and commit the output of npm run api:generate:',
        ...changed,
      ].join('\n- '),
    );
    process.exitCode = 1;
  } else {
    console.log('Generated API client is deterministic and up to date.');
  }
}

async function snapshot(directory) {
  const files = await collectFiles(directory);
  return new Map(
    await Promise.all(
      files.map(async (file) => [path.relative(directory, file), await readFile(file, 'utf8')]),
    ),
  );
}

async function collectFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(
    entries.map((entry) => {
      const target = path.join(directory, entry.name);
      return entry.isDirectory() ? collectFiles(target) : [target];
    }),
  );
  return nested.flat().sort();
}

function changedFiles(before, after) {
  const names = new Set([...before.keys(), ...after.keys()]);
  return [...names].filter((name) => before.get(name) !== after.get(name)).sort();
}
