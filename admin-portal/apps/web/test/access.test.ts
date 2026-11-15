import { describe, expect, it } from 'vitest';
import { resolveAccess } from '../src/auth/access';

const google = { firebase: { sign_in_provider: 'google.com' }, email_verified: true };
const password = { firebase: { sign_in_provider: 'password' } };

describe('resolveAccess', () => {
  it('requires a verified email', () => {
    expect(resolveAccess({ ...password, email_verified: false, role: 'platformAdmin' })).toEqual({
      kind: 'unverified',
    });
    expect(resolveAccess({ ...password, role: 'platformAdmin' })).toEqual({ kind: 'unverified' });
  });

  it('gives no role without a role claim, or with an unknown one', () => {
    expect(resolveAccess(google)).toEqual({ kind: 'noRole' });
    expect(resolveAccess({ ...google, role: 'admin' })).toEqual({ kind: 'noRole' });
    expect(resolveAccess({ ...google, role: 42 })).toEqual({ kind: 'noRole' });
  });

  it('accepts a platform admin with or without explicit org/sites', () => {
    const expected = { kind: 'staff', claims: { role: 'platformAdmin', org: null, sites: [] } };
    expect(resolveAccess({ ...google, role: 'platformAdmin' })).toEqual(expected);
    expect(resolveAccess({ ...google, role: 'platformAdmin', org: null, sites: [] })).toEqual(
      expected,
    );
  });

  it('accepts org-scoped roles with the invariants of users/{uid}', () => {
    expect(resolveAccess({ ...google, role: 'orgAdmin', org: 'org_a' })).toEqual({
      kind: 'staff',
      claims: { role: 'orgAdmin', org: 'org_a', sites: [] },
    });
    expect(resolveAccess({ ...google, role: 'operator', org: 'org_a', sites: ['s1'] })).toEqual({
      kind: 'staff',
      claims: { role: 'operator', org: 'org_a', sites: ['s1'] },
    });
    // Fails closed on malformed combinations.
    expect(resolveAccess({ ...google, role: 'orgAdmin' })).toEqual({ kind: 'noRole' });
    expect(resolveAccess({ ...google, role: 'platformAdmin', org: 'org_a' })).toEqual({
      kind: 'noRole',
    });
    expect(resolveAccess({ ...google, role: 'manager', org: 'org_a', sites: [] })).toEqual({
      kind: 'noRole',
    });
    expect(resolveAccess({ ...google, role: 'orgAdmin', org: 'a/b' })).toEqual({ kind: 'noRole' });
  });

  it('never gives an anonymous user (a TV) a staff role', () => {
    expect(
      resolveAccess({
        firebase: { sign_in_provider: 'anonymous' },
        email_verified: true,
        role: 'platformAdmin',
      }),
    ).toEqual({ kind: 'noRole' });
  });
});
