import { execFile } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { describeTarget, runPreCutoverAudit, writeDocs } from '../../src/admin/index.js';
import { exitCodeOf, needsAttention } from '../../src/audit/report.js';
import { loadContractFixtures, loadLegacyQueueFixtures } from '../../src/fixtures.js';
import { auth, clearFirestore, close, db, runScript, target } from './helpers.js';

const contract = loadContractFixtures();
const legacy = loadLegacyQueueFixtures();

beforeEach(clearFirestore);
afterAll(close);

/** Every document in the database with its update time: any write would change it. */
const snapshot = async () => {
  const collections = await db.listCollections();
  const docs = (await Promise.all(collections.map((c) => c.get()))).flatMap((s) => s.docs);
  return Object.fromEntries(
    docs.map((d) => [d.ref.path, { data: d.data(), updated: d.updateTime.toMillis() }]),
  );
};

describe('pre-cutover audit (emulator)', () => {
  it('reports each fixture queue and writes nothing', async () => {
    await writeDocs(db, { ...contract, ...legacy });
    const before = await snapshot();
    const report = await runPreCutoverAudit({ db, auth }, describeTarget(target));
    expect(await snapshot()).toEqual(before);

    expect(report.sections.map((s) => s.id)).toEqual(['queues']);
    const status = Object.fromEntries(
      report.sections[0]!.rows.map((r) => [r.id, needsAttention(r) ? 'attention' : 'ok']),
    );
    expect(status).toEqual({
      legacy_balcao_1: 'attention',
      legacy_balcao_2: 'attention',
      legacy_manual_3: 'attention',
      q_analises_Ce7y: 'ok',
      q_balcao_a_P4tc: 'ok',
      q_balcao_b_Lr6w: 'ok',
      q_consultas_Ma9v: 'attention',
    });
    expect(exitCodeOf(report)).toBe(1);
  });

  it('the script prints the table and exits 1 when a queue needs attention', async () => {
    await writeDocs(db, { ...contract, ...legacy });
    const res = await runScript('pre-cutover-audit.ts', []);
    expect(res.code, res.stderr).toBe(1);
    expect(res.stdout).toContain('Pre-cutover audit of Firestore emulator');
    expect(res.stdout).toContain('== Queues: 7 checked, 4 need attention ==');
    expect(res.stdout).toMatch(/^ATTENTION {2}q_consultas_Ma9v {2}Consultas/m);
    expect(res.stdout).toContain('Result: 4 items need attention before the cutover.');
  });

  it('--json is machine-readable, and a clean database exits 0', async () => {
    await writeDocs(db, { 'queues/q_balcao_a_P4tc': contract['queues/q_balcao_a_P4tc'] });
    const res = await runScript('pre-cutover-audit.ts', ['--json']);
    expect(res.code, res.stderr).toBe(0);
    expect(JSON.parse(res.stdout)).toMatchObject({
      attention: 0,
      sections: [{ id: 'queues', rows: [{ id: 'q_balcao_a_P4tc', status: 'ok', findings: [] }] }],
    });
  });

  it('refuses a real project while the emulator env is set, and needs a target', async () => {
    const real = await runScript('pre-cutover-audit.ts', ['--project', 'qms-dev']);
    expect(real.code).toBe(2);
    expect(real.stderr).toContain('FIRESTORE_EMULATOR_HOST is set');

    const env = { ...process.env };
    delete env.FIRESTORE_EMULATOR_HOST;
    const none = await runScript('pre-cutover-audit.ts', [], { env });
    expect(none.code).toBe(2);
    expect(none.stderr).toContain('Pass --project <id>');
  });

  it('the repo-root wrapper runs the same audit and prints help', async () => {
    await writeDocs(db, { ...contract });
    const wrapper = fileURLToPath(
      new URL('../../../../../scripts/pre-cutover-audit.sh', import.meta.url),
    );
    const run = (args: string[]) =>
      new Promise<{ code: number; stdout: string }>((resolve) => {
        execFile(wrapper, args, (err, stdout) => {
          resolve({ code: err ? (typeof err.code === 'number' ? err.code : 1) : 0, stdout });
        });
      });
    const audit = await run(['--json']);
    expect(audit.code).toBe(1);
    expect(JSON.parse(audit.stdout)).toMatchObject({ attention: 1 });
    const help = await run(['--help']);
    expect(help.code).toBe(0);
    expect(help.stdout).toContain('scripts/pre-cutover-audit.sh --project <firebase-project-id>');
  });
});
