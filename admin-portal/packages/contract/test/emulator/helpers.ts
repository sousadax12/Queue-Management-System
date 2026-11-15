import { execFile } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { connect, resolveTarget } from '../../src/admin/index.js';

export const target = resolveTarget();
if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error('Run emulator tests with `pnpm test:emulator` (firebase emulators:exec).');
}

export const { db, close } = connect(target);

/** Wipe the emulator's database between tests. */
export async function clearFirestore(): Promise<void> {
  if (target.kind !== 'emulator') throw new Error('refusing to clear a real project');
  const res = await fetch(
    `http://${target.host}/emulator/v1/projects/${target.projectId}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  if (!res.ok) throw new Error(`clear failed: ${res.status} ${await res.text()}`);
}

const pkgDir = fileURLToPath(new URL('../../', import.meta.url));

/** Run a package script (`seed`, `backfill:queues`) through tsx, like a user would. */
export async function runScript(
  script: 'seed.ts' | 'backfill-queues.ts',
  args: string[],
): Promise<{ code: number; stdout: string; stderr: string }> {
  try {
    const { stdout, stderr } = await promisify(execFile)(
      'pnpm',
      ['exec', 'tsx', `scripts/${script}`, ...args],
      { cwd: pkgDir, env: process.env },
    );
    return { code: 0, stdout, stderr };
  } catch (err) {
    const e = err as { code?: number; stdout?: string; stderr?: string };
    return { code: e.code ?? 1, stdout: e.stdout ?? '', stderr: e.stderr ?? '' };
  }
}
