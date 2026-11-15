import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { userSchema } from '../../src/index.js';
import { fromFirestore, grantPlatformAdmin } from '../../src/admin/index.js';
import { auth, clearAuth, clearFirestore, close, db, runScript } from './helpers.js';

beforeEach(async () => {
  await Promise.all([clearFirestore(), clearAuth()]);
});
afterAll(close);

describe('grant-platform-admin (emulator)', () => {
  it('sets the claim, keeps other claims and mirrors users/{uid}', async () => {
    const u = await auth.createUser({ email: 'ana@example.com', displayName: 'Ana' });
    await auth.setCustomUserClaims(u.uid, { role: 'operator', org: 'org_a', sites: ['s1'], x: 1 });

    const r = await runScript('grant-platform-admin.ts', ['--email', 'ana@example.com']);
    expect(r.stderr).toBe('');
    expect(r.code).toBe(0);
    expect(r.stdout).toMatch(/Granted platformAdmin to ana@example.com .*was operator/);

    const after = await auth.getUser(u.uid);
    expect(after.customClaims).toEqual({ role: 'platformAdmin', org: null, sites: [], x: 1 });

    const snap = await db.doc(`users/${u.uid}`).get();
    const doc = userSchema.parse(fromFirestore(snap.data()));
    expect(doc).toMatchObject({
      email: 'ana@example.com',
      displayName: 'Ana',
      role: 'platformAdmin',
      orgId: null,
      sites: [],
      updatedBy: 'script:grant-platform-admin',
    });
  });

  it('looks users up by uid and is idempotent', async () => {
    const u = await auth.createUser({ email: 'bo@example.com' });
    await grantPlatformAdmin(auth, db, { uid: u.uid, by: 'test' });
    const again = await grantPlatformAdmin(auth, db, { uid: u.uid, by: 'test' });
    expect(again.previousRole).toBe('platformAdmin');
    expect(again.user.displayName).toBeNull();
  });

  it('fails clearly for an unknown user or bad arguments', async () => {
    const missing = await runScript('grant-platform-admin.ts', ['--email', 'nobody@example.com']);
    expect(missing.code).toBe(1);
    expect(missing.stderr).toMatch(/user_not_found/);

    const both = await runScript('grant-platform-admin.ts', ['--email', 'a@b.c', '--uid', 'x']);
    expect(both.code).toBe(2);
  });
});
