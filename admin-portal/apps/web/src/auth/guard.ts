import { ROLES, type CustomClaims, type Role } from '@qms/contract';
import { redirect } from '@tanstack/react-router';
import type { AuthState } from './context';

/** Roles that may open the portal shell. Pages narrow this further as they arrive. */
export const PORTAL_ROLES: readonly Role[] = ROLES;

/** Only same-origin paths, so `?redirect=` can't send users to another site. */
export function safeRedirect(target: unknown): string {
  return typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')
    ? target
    : '/';
}

/**
 * Route guard for `beforeLoad`: returns the user's claims when their `role` is one of
 * `allowed`, else redirects to sign-in, email verification or "no access".
 */
export function requireRole(auth: AuthState, allowed: readonly Role[], href: string): CustomClaims {
  if (auth.status !== 'signedIn') {
    // eslint-disable-next-line @typescript-eslint/only-throw-error -- TanStack Router's redirect
    throw redirect({ to: '/sign-in', search: { redirect: href } });
  }
  if (auth.access.kind === 'unverified') {
    // eslint-disable-next-line @typescript-eslint/only-throw-error
    throw redirect({ to: '/verify-email' });
  }
  if (auth.access.kind !== 'staff' || !allowed.includes(auth.access.claims.role)) {
    // eslint-disable-next-line @typescript-eslint/only-throw-error
    throw redirect({ to: '/no-access' });
  }
  return auth.access.claims;
}
