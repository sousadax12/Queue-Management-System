import { execFile } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { connect, resolveTarget } from '../../src/admin/index.js';

export const target = resolveTarget();
if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error('Run emulator tests with `pnpm test:emulator` (firebase emulators:exec).');
}

export const { db, auth, close } = connect(target);

/** Wipe the emulator's database between tests. */
export async function clearFirestore(): Promise<void> {
  if (target.kind !== 'emulator') throw new Error('refusing to clear a real project');
  const res = await fetch(
    `http://${target.host}/emulator/v1/projects/${target.projectId}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  if (!res.ok) throw new Error(`clear failed: ${res.status} ${await res.text()}`);
}

/** Delete every Auth emulator user between tests. */
export async function clearAuth(): Promise<void> {
  if (target.kind !== 'emulator') throw new Error('refusing to clear a real project');
  const host = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9099';
  const res = await fetch(`http://${host}/emulator/v1/projects/${target.projectId}/accounts`, {
    method: 'DELETE',
  });
  if (!res.ok) throw new Error(`clear auth failed: ${res.status} ${await res.text()}`);
}

const pkgDir = fileURLToPath(new URL('../../', import.meta.url));

/** Run a package script (`seed`, `backfill:queues`) through tsx, like a user would. */
export async function runScript(
  script:
    | 'seed.ts'
    | 'backfill-queues.ts'
    | 'pre-cutover-audit.ts'
    | 'grant-platform-admin.ts'
    | 'grant-operator.ts',
  args: string[],
  opts: { stdin?: string; env?: NodeJS.ProcessEnv } = {},
): Promise<{ code: number; stdout: string; stderr: string }> {
  return new Promise((resolve) => {
    const child = execFile(
      'pnpm',
      ['exec', 'tsx', `scripts/${script}`, ...args],
      { cwd: pkgDir, env: opts.env ?? process.env },
      (err, stdout, stderr) => {
        const code = err ? (typeof err.code === 'number' ? err.code : 1) : 0;
        resolve({ code, stdout, stderr });
      },
    );
    child.stdin?.end(opts.stdin ?? '');
  });
}
