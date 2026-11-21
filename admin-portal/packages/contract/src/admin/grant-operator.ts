import { randomBytes } from 'node:crypto';
import type { Auth, UserRecord } from 'firebase-admin/auth';
import type { Firestore } from 'firebase-admin/firestore';
import { z } from 'zod';
import { paths } from '../collections.js';
import { SERVER_TIMESTAMP } from '../encoding.js';
import { docIdSchema } from '../schemas/common.js';
import { type CustomClaims, type UserDoc, userSchema } from '../schemas/users.js';
import { toFirestore } from './codec.js';
import { GrantError } from './grant.js';

/** The roles `grant-operator` hands out: both act on the queues of some sites of one org. */
export const OPERATOR_ROLES = ['operator', 'manager'] as const;
export type OperatorRole = (typeof OPERATOR_ROLES)[number];

/**
 * The operator's queue query is `orgId == org` and `siteId in sites`, and Firestore caps `in`
 * at 30 values, so an account with more sites could never see all of its queues.
 */
export const MAX_OPERATOR_SITES = 30;
export const MIN_PASSWORD_LENGTH = 8;

export interface GrantOperatorInput {
  email: string;
  role: OperatorRole;
  org: string;
  /** Sites of `org` the account may operate. Duplicates are dropped; order is kept. */
  sites: readonly string[];
  /** Display name for a new account, or a new one for an existing account. */
  displayName?: string | undefined;
  /**
   * The password for a new account. Called only when the account is created (never on a dry
   * run or for an existing account). Omitted = a random password, returned once in the result.
   */
  password?: (() => Promise<string>) | undefined;
  /** Validate and report, but write nothing. */
  dryRun?: boolean | undefined;
  /** `updatedBy` on the `users/{uid}` mirror. */
  by: string;
}

export interface GrantOperatorResult {
  /** Null on a dry run that would create the account. */
  uid: string | null;
  email: string;
  /** The account did not exist (and, unless this is a dry run, was created). */
  created: boolean;
  /** The random password of a new account; null when one was supplied or nothing was created. */
  generatedPassword: string | null;
  previousRole: string | null;
  claims: CustomClaims;
  user: UserDoc;
  dryRun: boolean;
}

const inputSchema = z.strictObject({
  email: z.email(),
  role: z.enum(OPERATOR_ROLES),
  org: docIdSchema,
  sites: z
    .array(docIdSchema)
    .min(1, 'at least one site is required')
    .max(MAX_OPERATOR_SITES, {
      message: `at most ${MAX_OPERATOR_SITES} sites (Firestore caps the operator's "siteId in" query at ${MAX_OPERATOR_SITES})`,
    }),
});

export function generatePassword(): string {
  // 18 random bytes = 24 base64url characters.
  return randomBytes(18).toString('base64url');
}

async function findUser(auth: Auth, email: string): Promise<UserRecord | null> {
  try {
    return await auth.getUserByEmail(email);
  } catch (err) {
    if ((err as { code?: string }).code === 'auth/user-not-found') return null;
    throw err;
  }
}

/** The org must exist and every site must be one of its sites. */
async function checkScope(db: Firestore, org: string, sites: readonly string[]): Promise<void> {
  const orgSnap = await db.doc(paths.org(org)).get();
  if (!orgSnap.exists) throw new GrantError('org_not_found', `no org ${org} (orgs/${org})`);
  const snaps = await db.getAll(...sites.map((s) => db.doc(paths.site(s))));
  for (const [i, snap] of snaps.entries()) {
    const site = sites[i] ?? '';
    if (!snap.exists) throw new GrantError('site_not_found', `no site ${site} (sites/${site})`);
    const siteOrg: unknown = snap.get('orgId');
    if (siteOrg !== org) {
      throw new GrantError(
        'site_wrong_org',
        `site ${site} belongs to org ${String(siteOrg)}, not ${org}`,
      );
    }
  }
}

/**
 * Create (or update) an operator or manager account that `firestore.rules` v1 accepts: an
 * email/password user with `emailVerified: true` and the custom claims
 * `{role, org, sites}`, mirrored into `users/{uid}`.
 *
 * Refuses an unknown org, a site that doesn't exist or belongs to another org, an empty site
 * list, and an existing account that is an admin or belongs to another org. For an existing
 * account the role and sites are replaced (not merged), other custom claims are kept, and the
 * password is left alone. Idempotent.
 */
export async function grantOperator(
  auth: Auth,
  db: Firestore,
  input: GrantOperatorInput,
): Promise<GrantOperatorResult> {
  const parsed = inputSchema.safeParse({
    email: input.email,
    role: input.role,
    org: input.org,
    sites: [...new Set(input.sites)],
  });
  if (!parsed.success) {
    throw new GrantError('bad_input', z.prettifyError(parsed.error).replaceAll('\n', '; '));
  }
  const { email, role, org, sites } = parsed.data;
  const dryRun = input.dryRun ?? false;

  await checkScope(db, org, sites);

  const existing = await findUser(auth, email);
  const previous = existing?.customClaims ?? {};
  const previousRole = typeof previous.role === 'string' ? previous.role : null;
  const previousOrg: unknown = previous.org;
  if (previousRole !== null && !(OPERATOR_ROLES as readonly string[]).includes(previousRole)) {
    throw new GrantError(
      'role_conflict',
      `${email} is ${previousRole}; grant-operator never demotes an admin`,
    );
  }
  if (previousRole !== null && previousOrg != null && previousOrg !== org) {
    throw new GrantError(
      'role_conflict',
      `${email} is ${previousRole} of org ${JSON.stringify(previousOrg)}; grant-operator never moves an account to another org`,
    );
  }

  const claims: CustomClaims = { role, org, sites };
  const displayName = input.displayName ?? existing?.displayName ?? null;
  const user = userSchema.parse({
    email,
    displayName,
    role,
    orgId: org,
    sites,
    updatedAt: SERVER_TIMESTAMP,
    updatedBy: input.by,
  });
  const result = {
    email,
    created: existing === null,
    previousRole,
    claims,
    user,
    dryRun,
  };
  if (dryRun) {
    return { ...result, uid: existing?.uid ?? null, generatedPassword: null };
  }

  let uid: string;
  let generatedPassword: string | null = null;
  if (existing === null) {
    let password: string;
    if (input.password) {
      password = await input.password();
    } else {
      password = generatePassword();
      generatedPassword = password;
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      throw new GrantError(
        'bad_input',
        `the password needs at least ${MIN_PASSWORD_LENGTH} characters`,
      );
    }
    const created = await auth.createUser({
      email,
      password,
      emailVerified: true,
      ...(input.displayName !== undefined ? { displayName: input.displayName } : {}),
    });
    uid = created.uid;
  } else {
    uid = existing.uid;
    // The rules ignore the role of an unverified account. Whoever runs this vouches for it.
    if (!existing.emailVerified || input.displayName !== undefined) {
      await auth.updateUser(uid, {
        emailVerified: true,
        ...(input.displayName !== undefined ? { displayName: input.displayName } : {}),
      });
    }
  }

  // Claims are the source of truth; the mirror follows. A failed mirror write is fixed by
  // re-running the script.
  await auth.setCustomUserClaims(uid, { ...previous, ...claims });
  await db.doc(paths.user(uid)).set(toFirestore(user) as Record<string, unknown>);
  return { ...result, uid, generatedPassword };
}
