import { type CustomClaims, customClaimsSchema } from '@qms/contract';

/** What a signed-in user may do in the portal, from their ID token claims. */
export type Access =
  { kind: 'unverified' } | { kind: 'noRole' } | { kind: 'staff'; claims: CustomClaims };

/**
 * Decide access from decoded ID-token claims. Fails closed: anything malformed is `noRole`.
 *
 * - An anonymous user (a TV) never gets a staff role, even with a claim (design P5).
 * - An unverified email is `unverified` (Google accounts are always verified).
 * - The role claims must match the contract's `customClaimsSchema`, and the same invariants
 *   as `users/{uid}`: only platformAdmin has no org; managers and operators need sites.
 */
export function resolveAccess(claims: Readonly<Record<string, unknown>>): Access {
  const firebase = claims.firebase as { sign_in_provider?: unknown } | undefined;
  if (firebase?.sign_in_provider === 'anonymous') return { kind: 'noRole' };
  if (claims.email_verified !== true) return { kind: 'unverified' };

  const parsed = customClaimsSchema.safeParse({
    role: claims.role,
    org: claims.org ?? null,
    sites: claims.sites ?? [],
  });
  if (!parsed.success) return { kind: 'noRole' };
  const c = parsed.data;
  if ((c.role === 'platformAdmin') !== (c.org === null)) return { kind: 'noRole' };
  if ((c.role === 'manager' || c.role === 'operator') && c.sites.length === 0) {
    return { kind: 'noRole' };
  }
  return { kind: 'staff', claims: c };
}
