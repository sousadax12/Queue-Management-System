import type { Auth } from 'firebase-admin/auth';
import type { Firestore } from 'firebase-admin/firestore';
import { paths } from '../collections.js';
import { SERVER_TIMESTAMP } from '../encoding.js';
import { type CustomClaims, type UserDoc, userSchema } from '../schemas/users.js';
import { toFirestore } from './codec.js';

export const PLATFORM_ADMIN_CLAIMS: Readonly<CustomClaims> = {
  role: 'platformAdmin',
  org: null,
  sites: [],
};

export interface GrantPlatformAdminInput {
  /** Look the user up by uid… */
  uid?: string | undefined;
  /** …or by email. Exactly one is required. */
  email?: string | undefined;
  /** `updatedBy` on the `users/{uid}` mirror. */
  by: string;
}

export interface GrantResult {
  uid: string;
  email: string;
  previousRole: string | null;
  user: UserDoc;
}

export class GrantError extends Error {
  constructor(
    readonly code:
      | 'user_not_found'
      | 'no_email'
      | 'bad_input'
      | 'org_not_found'
      | 'site_not_found'
      | 'site_wrong_org'
      | 'role_conflict',
    message: string,
  ) {
    super(message);
    this.name = 'GrantError';
  }
}

/**
 * Make an existing Firebase Auth user a platform admin: set the `role` custom claim
 * (`{role: 'platformAdmin', org: null, sites: []}`) and mirror it into `users/{uid}`.
 * Other custom claims are kept. Idempotent. The user picks up the claim on their next
 * ID-token refresh (the portal's "no access" page has a "check again" button).
 */
export async function grantPlatformAdmin(
  auth: Auth,
  db: Firestore,
  input: GrantPlatformAdminInput,
): Promise<GrantResult> {
  if ((input.uid === undefined) === (input.email === undefined)) {
    throw new GrantError('bad_input', 'pass exactly one of uid or email');
  }
  let record;
  try {
    record =
      input.uid !== undefined
        ? await auth.getUser(input.uid)
        : await auth.getUserByEmail(input.email ?? '');
  } catch (err) {
    if ((err as { code?: string }).code === 'auth/user-not-found') {
      throw new GrantError(
        'user_not_found',
        `no Auth user with ${input.uid !== undefined ? `uid ${input.uid}` : `email ${input.email ?? ''}`}; they must sign in to the portal once first`,
      );
    }
    throw err;
  }
  if (!record.email) {
    throw new GrantError('no_email', `user ${record.uid} has no email (anonymous?)`);
  }

  const user = userSchema.parse({
    email: record.email,
    displayName: record.displayName ?? null,
    role: PLATFORM_ADMIN_CLAIMS.role,
    orgId: PLATFORM_ADMIN_CLAIMS.org,
    sites: PLATFORM_ADMIN_CLAIMS.sites,
    updatedAt: SERVER_TIMESTAMP,
    updatedBy: input.by,
  });
  const previous = record.customClaims ?? {};
  const previousRole = typeof previous.role === 'string' ? previous.role : null;

  // Claims are the source of truth; the mirror follows. A failed mirror write is fixed by
  // re-running the script.
  await auth.setCustomUserClaims(record.uid, { ...previous, ...PLATFORM_ADMIN_CLAIMS });
  await db.doc(paths.user(record.uid)).set(toFirestore(user) as Record<string, unknown>);
  return { uid: record.uid, email: record.email, previousRole, user };
}
