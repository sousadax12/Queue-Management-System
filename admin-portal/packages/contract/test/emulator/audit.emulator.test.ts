import { execFile } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import {
  describeTarget,
  grantOperator,
  runPreCutoverAudit,
  writeDocs,
} from '../../src/admin/index.js';
import { exitCodeOf, needsAttention } from '../../src/audit/report.js';
import { loadContractFixtures, loadLegacyQueueFixtures } from '../../src/fixtures.js';
import { auth, clearAuth, clearFirestore, close, db, runScript, target } from './helpers.js';

const contract = loadContractFixtures();
const legacy = loadLegacyQueueFixtures();

beforeEach(async () => {
  await Promise.all([clearFirestore(), clearAuth()]);
});
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

    expect(report.sections.map((s) => s.id)).toEqual(['queues', 'accounts']);
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
      sections: [
        { id: 'queues', rows: [{ id: 'q_balcao_a_P4tc', status: 'ok', findings: [] }] },
        { id: 'accounts', summary: { accounts: 0, withOperatorClaim: 0 }, rows: [] },
      ],
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

describe('pre-cutover audit: operator accounts (emulator)', () => {
  const ORG = 'org_lojas_7Hq2';
  const SITE = 'site_porto_3fKq';
  const claims = { role: 'operator', org: ORG, sites: [SITE] };

  /** One good operator (made by grant-operator), three broken ones, and two non-operators. */
  const seedAccounts = async () => {
    await writeDocs(db, contract);
    await grantOperator(auth, db, {
      email: 'porto@example.com',
      role: 'operator',
      org: ORG,
      sites: [SITE],
      by: 'test',
    });
    const create = async (
      email: string,
      user: { emailVerified: boolean; disabled?: boolean },
      customClaims?: Record<string, unknown>,
    ) => {
      const { uid } = await auth.createUser({ email, password: 'operator-test-123', ...user });
      if (customClaims) await auth.setCustomUserClaims(uid, customClaims);
    };
    await create('unverified@example.com', { emailVerified: false }, claims);
    await create('disabled@example.com', { emailVerified: true, disabled: true }, claims);
    await create('nosites@example.com', { emailVerified: true }, { role: 'operator', org: ORG });
    await create('visitor@example.com', { emailVerified: false });
    await create('beta@example.com', { emailVerified: true }, { beta: true });
  };

  /** Every account as Auth holds it: any change to one would show here. */
  const accounts = async () =>
    (await auth.listUsers()).users
      .map((u) => u.toJSON())
      .sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)));

  it('lists accounts with an operator claim, counts the others, and changes nothing', async () => {
    await seedAccounts();
    const before = { docs: await snapshot(), accounts: await accounts() };
    const report = await runPreCutoverAudit({ db, auth }, describeTarget(target));
    expect({ docs: await snapshot(), accounts: await accounts() }).toEqual(before);

    const section = report.sections.find((s) => s.id === 'accounts');
    expect(section?.summary).toEqual({
      accounts: 6,
      withOperatorClaim: 4,
      withoutOperatorClaim: 2,
    });
    expect(section?.notes?.[0]).toContain('4 with an operator claim (listed), 2 without');
    expect(
      Object.fromEntries(section?.rows.map((r) => [r.id, r.details?.nativeAccess]) ?? []),
    ).toEqual({
      'disabled@example.com': 'disabled',
      'nosites@example.com': 'no-operator-role',
      'porto@example.com': 'granted',
      'unverified@example.com': 'email-not-verified',
    });
    expect(section?.rows.find((r) => r.id === 'porto@example.com')).toMatchObject({
      findings: [],
      details: {
        emailVerified: true,
        disabled: false,
        claims,
        scope: { orgId: ORG, sites: [SITE] },
      },
    });
    expect(section?.rows.filter(needsAttention).length).toBe(3);
  });

  it('the script exits 1 for an unverified operator and 0 once it is verified', async () => {
    await writeDocs(db, { 'queues/q_balcao_a_P4tc': contract['queues/q_balcao_a_P4tc'] });
    const { uid } = await auth.createUser({
      email: 'porto@example.com',
      password: 'operator-test-123',
      emailVerified: false,
    });
    await auth.setCustomUserClaims(uid, claims);

    const table = await runScript('pre-cutover-audit.ts', []);
    expect(table.code, table.stderr).toBe(1);
    expect(table.stdout).toContain('== Queues: 1 checked, 0 need attention ==');
    expect(table.stdout).toContain('== Operator accounts: 1 checked, 1 need attention ==');
    expect(table.stdout).toMatch(/^ATTENTION {2}porto@example.com {2}operator .* NO /m);
    expect(table.stdout).toContain('[!] The email is not verified');

    const json = await runScript('pre-cutover-audit.ts', ['--json']);
    expect(json.code).toBe(1);
    expect(JSON.parse(json.stdout)).toMatchObject({
      attention: 1,
      sections: [
        { id: 'queues', attention: 0 },
        {
          id: 'accounts',
          attention: 1,
          rows: [{ id: 'porto@example.com', uid, emailVerified: false, disabled: false, claims }],
        },
      ],
    });

    // The other section alone is clean, and an unknown one is a usage error.
    expect((await runScript('pre-cutover-audit.ts', ['--section', 'queues'])).code).toBe(0);
    expect((await runScript('pre-cutover-audit.ts', ['--section', 'nope'])).code).toBe(2);

    await auth.updateUser(uid, { emailVerified: true });
    const fixed = await runScript('pre-cutover-audit.ts', ['--section', 'accounts']);
    expect(fixed.code, fixed.stderr).toBe(0);
    expect(fixed.stdout).not.toContain('== Queues');
    expect(fixed.stdout).toContain('Result: nothing needs attention.');
  });
});
