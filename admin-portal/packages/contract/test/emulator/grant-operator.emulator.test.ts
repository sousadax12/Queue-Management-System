import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { userSchema } from '../../src/index.js';
import {
  GrantError,
  fromFirestore,
  grantOperator,
  toFirestore,
  writeDocs,
} from '../../src/admin/index.js';
import { loadContractFixtures, loadFixtureFile } from '../../src/fixtures.js';
import { auth, clearAuth, clearFirestore, close, db, runScript } from './helpers.js';

const ORG_A = 'org_lojas_7Hq2';
const SITE_A = 'site_porto_3fKq';
const SITE_A2 = 'site_braga_T7vm';
const ORG_B = 'org_clinica_K9xP';
const SITE_B = 'site_lisboa_Wd2e';

beforeEach(async () => {
  await Promise.all([clearFirestore(), clearAuth()]);
  await writeDocs(db, loadContractFixtures());
  // A second site in org A, so an account can hold more than one.
  const siteA = loadFixtureFile('sites.json')[`sites/${SITE_A}`] as Record<string, unknown>;
  await db
    .doc(`sites/${SITE_A2}`)
    .set(toFirestore({ ...siteA, name: 'Loja Braga' }) as Record<string, unknown>);
});
afterAll(close);

const grant = (over: Partial<Parameters<typeof grantOperator>[2]> = {}) =>
  grantOperator(auth, db, {
    email: 'porto@example.com',
    role: 'operator',
    org: ORG_A,
    sites: [SITE_A],
    by: 'test',
    ...over,
  });

const mirror = async (uid: string) =>
  userSchema.parse(fromFirestore((await db.doc(`users/${uid}`).get()).data()));

const userCount = async () => (await auth.listUsers()).users.length;

describe('grant-operator (emulator)', () => {
  it('creates a verified account with claims and a valid users/{uid} mirror', async () => {
    const r = await runScript('grant-operator.ts', [
      '--email',
      'porto@example.com',
      '--org',
      ORG_A,
      '--site',
      SITE_A,
      '--name',
      'Balcão Porto',
    ]);
    expect(r.stderr).toBe('');
    expect(r.code).toBe(0);
    expect(r.stdout).toMatch(/Created porto@example.com \(.+\) as operator of org org_lojas_7Hq2/);
    const password = /Password \(shown once.*\): (\S+)/.exec(r.stdout)?.[1];
    expect(password).toHaveLength(24);

    const user = await auth.getUserByEmail('porto@example.com');
    expect(user.emailVerified).toBe(true);
    expect(user.displayName).toBe('Balcão Porto');
    expect(user.customClaims).toEqual({ role: 'operator', org: ORG_A, sites: [SITE_A] });
    expect(await mirror(user.uid)).toMatchObject({
      email: 'porto@example.com',
      displayName: 'Balcão Porto',
      role: 'operator',
      orgId: ORG_A,
      sites: [SITE_A],
      updatedBy: 'script:grant-operator',
    });
  });

  it('takes the password from --prompt-password (stdin) and creates managers', async () => {
    const r = await runScript(
      'grant-operator.ts',
      [
        '--email',
        'gerente@example.com',
        '--org',
        ORG_A,
        '--site',
        `${SITE_A},${SITE_A2}`,
        '--role',
        'manager',
        '--prompt-password',
      ],
      { stdin: 's3cret-passw0rd\n' },
    );
    expect(r.stderr).toBe('');
    expect(r.code).toBe(0);
    expect(r.stdout).not.toMatch(/Password \(shown once/);
    const user = await auth.getUserByEmail('gerente@example.com');
    expect(user.customClaims).toEqual({ role: 'manager', org: ORG_A, sites: [SITE_A, SITE_A2] });
    expect(user.passwordHash).toBeDefined();
  });

  it('updates an existing account: verifies it, replaces role and sites, keeps other claims', async () => {
    const u = await auth.createUser({ email: 'porto@example.com', password: 'original-pw' });
    await auth.setCustomUserClaims(u.uid, { role: 'manager', org: ORG_A, sites: [SITE_A], x: 1 });

    const r = await grant({ sites: [SITE_A2, SITE_A2] });
    expect(r).toMatchObject({ uid: u.uid, created: false, previousRole: 'manager' });
    expect(r.generatedPassword).toBeNull();

    const after = await auth.getUser(u.uid);
    expect(after.emailVerified).toBe(true);
    expect(after.customClaims).toEqual({ role: 'operator', org: ORG_A, sites: [SITE_A2], x: 1 });
    expect((await mirror(u.uid)).sites).toEqual([SITE_A2]);

    // Idempotent.
    const again = await grant({ sites: [SITE_A2] });
    expect(again.previousRole).toBe('operator');
  });

  it('refuses an unknown org, an unknown or foreign site, and an empty site list', async () => {
    const cases: [Partial<Parameters<typeof grantOperator>[2]>, string][] = [
      [{ org: 'org_nope' }, 'org_not_found'],
      [{ sites: ['site_nope'] }, 'site_not_found'],
      [{ sites: [SITE_A, SITE_B] }, 'site_wrong_org'],
      [{ org: ORG_B, sites: [SITE_A] }, 'site_wrong_org'],
      [{ sites: [] }, 'bad_input'],
      [{ email: 'not-an-email' }, 'bad_input'],
      [{ sites: Array.from({ length: 31 }, (_, i) => `s${String(i)}`) }, 'bad_input'],
    ];
    for (const [over, code] of cases) {
      await expect(grant(over), JSON.stringify(over)).rejects.toMatchObject({
        name: 'GrantError',
        code,
      });
    }
    expect(await userCount()).toBe(0);
    expect((await db.collection('users').get()).size).toBe(
      Object.keys(loadFixtureFile('users.json')).length,
    );

    const cli = await runScript('grant-operator.ts', [
      '--email',
      'x@example.com',
      '--org',
      ORG_A,
      '--site',
      SITE_B,
    ]);
    expect(cli.code).toBe(1);
    expect(cli.stderr).toMatch(/site_wrong_org/);

    const noSite = await runScript('grant-operator.ts', [
      '--email',
      'x@example.com',
      '--org',
      ORG_A,
    ]);
    expect(noSite.code).toBe(2);
  });

  it('never demotes an admin or moves an account to another org', async () => {
    const admin = await auth.createUser({ email: 'admin@example.com' });
    await auth.setCustomUserClaims(admin.uid, { role: 'orgAdmin', org: ORG_A, sites: [] });
    await expect(grant({ email: 'admin@example.com' })).rejects.toMatchObject({
      code: 'role_conflict',
    });

    const other = await auth.createUser({ email: 'lisboa@example.com' });
    await auth.setCustomUserClaims(other.uid, { role: 'operator', org: ORG_B, sites: [SITE_B] });
    await expect(grant({ email: 'lisboa@example.com' })).rejects.toBeInstanceOf(GrantError);
    expect((await auth.getUser(other.uid)).customClaims).toMatchObject({ org: ORG_B });
  });

  it('--dry-run validates and writes nothing', async () => {
    const r = await runScript('grant-operator.ts', [
      '--email',
      'porto@example.com',
      '--org',
      ORG_A,
      '--site',
      SITE_A,
      '--dry-run',
    ]);
    expect(r.stderr).toBe('');
    expect(r.code).toBe(0);
    expect(r.stdout).toMatch(/Dry run: would create porto@example.com as operator/);
    expect(await userCount()).toBe(0);

    const bad = await runScript('grant-operator.ts', [
      '--email',
      'porto@example.com',
      '--org',
      ORG_A,
      '--site',
      SITE_B,
      '--dry-run',
    ]);
    expect(bad.code).toBe(1);
    expect(bad.stderr).toMatch(/site_wrong_org/);
  });

  it('refuses the production project without --production', async () => {
    const env = { ...process.env };
    delete env.FIRESTORE_EMULATOR_HOST;
    delete env.FIREBASE_AUTH_EMULATOR_HOST;
    const r = await runScript(
      'grant-operator.ts',
      ['--email', 'x@example.com', '--org', ORG_A, '--site', SITE_A, '--project', 'santos-1b6f6'],
      { env },
    );
    expect(r.code).toBe(1);
    expect(r.stderr).toMatch(/production project; refusing/);
  });
});
