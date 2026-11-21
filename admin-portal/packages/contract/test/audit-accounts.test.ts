import { describe, expect, it } from 'vitest';
import {
  type AccountRecord,
  auditAccount,
  hasOperatorClaim,
  operatorScopeFromClaims,
} from '../src/audit/accounts.js';
import { exitCodeOf, needsAttention, renderReport, reportToJson } from '../src/audit/report.js';

const OPERATOR = { role: 'operator', org: 'org_a', sites: ['site_1', 'site_2'] };
const account = (over: Partial<AccountRecord> = {}): AccountRecord => ({
  uid: 'uid_1',
  email: 'porto@example.com',
  emailVerified: true,
  disabled: false,
  customClaims: OPERATOR,
  ...over,
});
const codes = (row: ReturnType<typeof auditAccount>, severity = 'attention') =>
  row.findings.filter((f) => f.severity === severity).map((f) => f.code);

// The cases of android-tv/domain/.../operator/OperatorScopeTest.kt.
describe('operatorScopeFromClaims', () => {
  it('gives each role the scope the native app gives it', () => {
    expect(operatorScopeFromClaims({ role: 'platformAdmin', org: 'x', sites: ['s'] })).toEqual({
      role: 'platformAdmin',
      orgId: null,
      sites: [],
    });
    expect(operatorScopeFromClaims({ role: 'orgAdmin', org: 'org_a' })).toEqual({
      role: 'orgAdmin',
      orgId: 'org_a',
      sites: [],
    });
    expect(operatorScopeFromClaims({ ...OPERATOR, role: 'manager' })).toEqual({
      role: 'manager',
      orgId: 'org_a',
      sites: ['site_1', 'site_2'],
    });
    expect(operatorScopeFromClaims({ ...OPERATOR, sites: ['site_1', '', 7, null] })).toEqual({
      role: 'operator',
      orgId: 'org_a',
      sites: ['site_1'],
    });
  });

  it.each([
    ['no claims', undefined],
    ['null claims', null],
    ['no role', { org: 'org_a', sites: ['site_1'] }],
    ['an unknown role', { ...OPERATOR, role: 'owner' }],
    ['a role with the wrong case', { ...OPERATOR, role: 'Operator' }],
    ['a non-string role', { ...OPERATOR, role: 1 }],
    ['an org admin without an org', { role: 'orgAdmin' }],
    ['an org admin with an empty org', { role: 'orgAdmin', org: '' }],
    ['an operator without an org', { role: 'operator', sites: ['site_1'] }],
    ['an operator without sites', { role: 'operator', org: 'org_a', sites: [] }],
    ['a manager whose sites is not a list', { role: 'manager', org: 'org_a', sites: 'site_1' }],
  ])('gives no scope to %s', (_name, claims) => {
    expect(operatorScopeFromClaims(claims)).toBeNull();
  });
});

describe('hasOperatorClaim', () => {
  it('is true for any of role, org or sites', () => {
    expect(hasOperatorClaim(OPERATOR)).toBe(true);
    expect(hasOperatorClaim({ role: 'owner' })).toBe(true);
    expect(hasOperatorClaim({ org: 'org_a' })).toBe(true);
    expect(hasOperatorClaim({ sites: [] })).toBe(true);
  });

  it('is false for accounts without them', () => {
    expect(hasOperatorClaim(undefined)).toBe(false);
    expect(hasOperatorClaim({})).toBe(false);
    expect(hasOperatorClaim({ role: null, beta: true })).toBe(false);
  });
});

describe('auditAccount', () => {
  it('passes a verified, enabled operator and reports its claims', () => {
    const row = auditAccount(account());
    expect(row.findings).toEqual([]);
    expect(row.id).toBe('porto@example.com');
    expect(row.cells).toEqual({
      role: 'operator',
      scope: 'org org_a: site_1, site_2',
      verified: 'yes',
      disabled: 'no',
      native: 'access',
    });
    expect(row.details).toEqual({
      uid: 'uid_1',
      email: 'porto@example.com',
      emailVerified: true,
      disabled: false,
      claims: OPERATOR,
      scope: { role: 'operator', orgId: 'org_a', sites: ['site_1', 'site_2'] },
      nativeAccess: 'granted',
    });
  });

  it('flags an unverified operator: the native app locks it out, Flutter does not', () => {
    const row = auditAccount(account({ emailVerified: false }));
    expect(codes(row)).toEqual(['email-not-verified']);
    expect(row.cells).toMatchObject({ verified: 'NO', native: 'no access (email-not-verified)' });
    expect(row.findings[0]?.message).toContain('"No operator access"');
  });

  it('flags a disabled operator', () => {
    const row = auditAccount(account({ disabled: true }));
    expect(codes(row)).toEqual(['account-disabled']);
    expect(row.cells).toMatchObject({ disabled: 'YES', native: 'no access (disabled)' });
  });

  it('reports every problem of an account, and the first one the app would hit', () => {
    const row = auditAccount(
      account({ disabled: true, emailVerified: false, customClaims: { role: 'operator' } }),
    );
    expect(codes(row)).toEqual(['account-disabled', 'email-not-verified', 'no-operator-scope']);
    expect(row.details).toMatchObject({ nativeAccess: 'disabled', scope: null });
  });

  it('flags claims that grant no scope, like the native no-role screen', () => {
    const row = auditAccount(account({ customClaims: { role: 'operator', org: 'org_a' } }));
    expect(codes(row)).toEqual(['no-operator-scope']);
    expect(row.cells).toMatchObject({ scope: 'none', native: 'no access (no-operator-role)' });
    expect(row.findings[0]?.message).toContain('role "operator", org "org_a", sites -');
  });

  it('checks admins by the same rules', () => {
    const admin = auditAccount(account({ customClaims: { role: 'platformAdmin' } }));
    expect(admin.findings).toEqual([]);
    expect(admin.cells.scope).toBe('every org');
    const orgAdmin = auditAccount(
      account({ emailVerified: false, customClaims: { role: 'orgAdmin', org: 'org_a' } }),
    );
    expect(orgAdmin.cells.scope).toBe('org org_a');
    expect(codes(orgAdmin)).toEqual(['email-not-verified']);
  });

  it('notes more sites than one queue query can hold', () => {
    const sites = Array.from({ length: 31 }, (_, i) => `site_${i}`);
    const row = auditAccount(account({ customClaims: { ...OPERATOR, sites } }));
    expect(needsAttention(row)).toBe(false);
    expect(codes(row, 'info')).toEqual(['too-many-sites']);
  });

  it('falls back to the uid for an account without an email', () => {
    const row = auditAccount(account({ email: undefined }));
    expect(row.id).toBe('uid_1');
    expect(row.details).toMatchObject({ email: null });
  });
});

describe('accounts section in the report', () => {
  const section = (rows: ReturnType<typeof auditAccount>[]) => ({
    target: 't',
    sections: [
      {
        id: 'accounts',
        title: 'Operator accounts',
        columns: [{ key: 'verified', header: 'VERIFIED' }],
        rows,
        notes: ['3 accounts in Firebase Auth: 1 with an operator claim (listed), 2 without.'],
        summary: { accounts: 3, withOperatorClaim: 1, withoutOperatorClaim: 2 },
      },
    ],
  });

  it('prints the notes and exits 1 for an unverified operator', () => {
    const report = section([auditAccount(account({ emailVerified: false }))]);
    expect(exitCodeOf(report)).toBe(1);
    const text = renderReport(report);
    expect(text).toContain('== Operator accounts: 1 checked, 1 need attention ==');
    expect(text).toContain('3 accounts in Firebase Auth: 1 with an operator claim');
    expect(text).toMatch(/^ATTENTION {2}porto@example.com {2}NO$/m);
    expect(reportToJson(report)).toMatchObject({
      attention: 1,
      sections: [
        {
          id: 'accounts',
          summary: { accounts: 3, withOperatorClaim: 1, withoutOperatorClaim: 2 },
          rows: [{ id: 'porto@example.com', status: 'attention', emailVerified: false }],
        },
      ],
    });
  });

  it('still prints the notes when no account is listed', () => {
    const report = section([]);
    expect(exitCodeOf(report)).toBe(0);
    expect(renderReport(report)).toContain('3 accounts in Firebase Auth');
    expect(renderReport(report)).toContain('(nothing to list)');
  });
});
