/**
 * Whether one Firebase Auth account can use the native operator app. Pure: no Firebase, no I/O.
 * It mirrors, by hand, how the native app decides access:
 *
 * - `OperatorScope.fromClaims` and `StaffRole`
 *   (`android-tv/domain/.../tv/domain/operator/OperatorScope.kt`): the custom claims
 *   `{role, org, sites}` give a scope, or none;
 * - `OperatorSession.accessOf`
 *   (`android-tv/operator/.../operator/session/OperatorSession.kt`): `email_verified` must be
 *   true **before** the claims are even looked at, else the app shows "No operator access".
 *
 * The Flutter app (`lib/auth/operator_scope.dart`, `loadOperatorScope`) reads the same claims
 * but never checks `email_verified`, so an unverified account that works on Flutter today is
 * locked out after the cutover.
 */
import { ROLES, type Role } from '../schemas/users.js';
import type { AuditColumn, AuditRow, Finding } from './report.js';

/** Firestore caps `whereIn` at 30 values (`OperatorScope.MAX_SITES_PER_QUERY`). */
export const MAX_SITES_PER_QUERY = 30;

/** The claims that make an account an operator account for this audit. */
const OPERATOR_CLAIMS = ['role', 'org', 'sites'] as const;

export interface OperatorScope {
  role: Role;
  /** Null only for platform admins. */
  orgId: string | null;
  sites: string[];
}

/**
 * A port of `OperatorScope.fromClaims`: the scope the claims grant, or null (no role, an unknown
 * role, or a role without the org or sites it needs).
 */
export function operatorScopeFromClaims(
  claims: Record<string, unknown> | null | undefined,
): OperatorScope | null {
  if (!claims) return null;
  const role = ROLES.find((r) => r === claims.role);
  if (role === undefined) return null;
  const orgId = typeof claims.org === 'string' && claims.org !== '' ? claims.org : null;
  const sites = Array.isArray(claims.sites)
    ? claims.sites.filter((s): s is string => typeof s === 'string' && s !== '')
    : [];
  switch (role) {
    case 'platformAdmin':
      return { role, orgId: null, sites: [] };
    case 'orgAdmin':
      return orgId === null ? null : { role, orgId, sites: [] };
    case 'manager':
    case 'operator':
      return orgId === null || sites.length === 0 ? null : { role, orgId, sites };
  }
}

/** True when the account carries any of the custom claims the operator apps read. */
export const hasOperatorClaim = (claims: Record<string, unknown> | null | undefined): boolean =>
  claims != null && OPERATOR_CLAIMS.some((c) => claims[c] !== undefined && claims[c] !== null);

/** The fields of an Admin SDK `UserRecord` the audit reads. */
export interface AccountRecord {
  uid: string;
  email?: string | undefined;
  emailVerified: boolean;
  disabled: boolean;
  customClaims?: Record<string, unknown> | undefined;
}

/** What the native app does when this account signs in. */
export type NativeAccess = 'granted' | 'disabled' | 'email-not-verified' | 'no-operator-role';

export interface AccountAuditDetails extends Record<string, unknown> {
  uid: string;
  email: string | null;
  emailVerified: boolean;
  disabled: boolean;
  /** The raw custom claims the operator apps read. */
  claims: { role: unknown; org: unknown; sites: unknown };
  /** The scope the native app derives from them; null when they grant none. */
  scope: OperatorScope | null;
  nativeAccess: NativeAccess;
}

export const ACCOUNT_COLUMNS: AuditColumn[] = [
  { key: 'role', header: 'ROLE' },
  { key: 'scope', header: 'SCOPE' },
  { key: 'verified', header: 'VERIFIED' },
  { key: 'disabled', header: 'DISABLED' },
  { key: 'native', header: 'NATIVE APP' },
];

const show = (v: unknown): string => (v === undefined ? '-' : JSON.stringify(v));

function scopeCell(scope: OperatorScope | null): string {
  if (scope === null) return 'none';
  if (scope.role === 'platformAdmin') return 'every org';
  if (scope.role === 'orgAdmin') return `org ${scope.orgId}`;
  return `org ${scope.orgId}: ${scope.sites.join(', ')}`;
}

/** Audits one account that has an operator claim (see {@link hasOperatorClaim}). */
export function auditAccount(account: AccountRecord): AuditRow {
  const claims = account.customClaims ?? {};
  const scope = operatorScopeFromClaims(claims);
  const findings: Finding[] = [];
  const attention = (code: string, message: string) =>
    findings.push({ code, severity: 'attention', message });

  if (account.disabled) {
    attention(
      'account-disabled',
      'The account is disabled: it cannot sign in on either app. Enable it (Firebase console, Authentication > Users), or stop using it on a box.',
    );
  }
  if (!account.emailVerified) {
    attention(
      'email-not-verified',
      'The email is not verified: the native app signs this account out and shows "No operator access", while the Flutter app does not check this at sign-in. Verify it before cutting over any box that uses it.',
    );
  }
  if (scope === null) {
    attention(
      'no-operator-scope',
      `The claims (role ${show(claims.role)}, org ${show(claims.org)}, sites ${show(claims.sites)}) grant no operator scope: the native app shows "No operator access", as Flutter does. Re-run \`pnpm grant-operator\` for it, or ignore it if no box uses this account.`,
    );
  } else if (scope.sites.length > MAX_SITES_PER_QUERY) {
    findings.push({
      code: 'too-many-sites',
      severity: 'info',
      message: `The account has ${scope.sites.length} sites: the operator apps only list the queues of the first ${MAX_SITES_PER_QUERY}.`,
    });
  }

  // The order of the native app: a disabled account fails at sign-in, then accessOf() checks
  // email_verified before the claims.
  const nativeAccess: NativeAccess = account.disabled
    ? 'disabled'
    : !account.emailVerified
      ? 'email-not-verified'
      : scope === null
        ? 'no-operator-role'
        : 'granted';
  const details: AccountAuditDetails = {
    uid: account.uid,
    email: account.email ?? null,
    emailVerified: account.emailVerified,
    disabled: account.disabled,
    claims: { role: claims.role ?? null, org: claims.org ?? null, sites: claims.sites ?? null },
    scope,
    nativeAccess,
  };
  return {
    id: account.email ?? account.uid,
    cells: {
      role: typeof claims.role === 'string' ? claims.role : show(claims.role),
      scope: scopeCell(scope),
      verified: account.emailVerified ? 'yes' : 'NO',
      disabled: account.disabled ? 'YES' : 'no',
      native: nativeAccess === 'granted' ? 'access' : `no access (${nativeAccess})`,
    },
    findings,
    details,
  };
}
