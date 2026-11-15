/**
 * Shared set-up for the rules tests: one emulator test environment loaded with the root
 * `firestore.rules`, the contract fixtures as seed data, and a Firestore handle per persona.
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  type RulesTestContext,
  type RulesTestEnvironment,
  type TokenOptions,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  type RunClaimTransaction,
  isEncodedTimestamp,
  isServerTimestamp,
  timestampFromParts,
  timestampParts,
  userSchema,
} from '@qms/contract';
import {
  loadContractFixtures,
  loadFixtureFile,
  loadLegacyQueueFixtures,
} from '@qms/contract/fixtures';
import {
  type Firestore,
  Timestamp,
  doc,
  runTransaction,
  serverTimestamp,
  writeBatch,
} from 'firebase/firestore';

export const RULES_PATH = fileURLToPath(new URL('../../../../firestore.rules', import.meta.url));
export const PROJECT_ID = 'demo-qms-rules';

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error('Run the rules tests with `pnpm test:emulator` (firebase emulators:exec).');
}

// ---------------------------------------------------------------- the data set (fixtures)

/** Org A ("Lojas") and org B ("Clínica"), one site each (contract fixtures/README.md). */
export const ORG_A = 'org_lojas_7Hq2';
export const SITE_A = 'site_porto_3fKq';
export const SCREEN_A = 'scr_lobby_left_Q2mz';
export const QUEUE_A = 'q_balcao_a_P4tc';
export const PLAYLIST_A_ORGWIDE = 'pl_promo_nov_Zx81';
export const MEDIA_A = 'md_bunny_8f2c';
export const TV_A = 'tvPortoLeft01uidAAAAAAAAAA';

export const ORG_B = 'org_clinica_K9xP';
export const SITE_B = 'site_lisboa_Wd2e';
export const SCREEN_B = 'scr_waiting_Hn5r';
export const QUEUE_B = 'q_consultas_Ma9v';
export const PLAYLIST_B_SITE = 'pl_saude_Rk42';
export const MEDIA_B = 'md_jellyfish_3762';
export const TV_B = 'tvLisboa01uidBBBBBBBBBBBBB';

export const TV_UNCLAIMED = 'tvUnclaimed01uidCCCCCCCCCC';
export const PAIRING_CODE = 'K7M3QX';
export const LEGACY_QUEUE = 'legacy_balcao_1';

export const UID = {
  platform: 'uid_platform_admin_P0wd',
  adminA: 'uid_lojas_admin_x81k',
  adminB: 'uid_clinica_admin_m2Qe',
  operatorB: 'uid_clinica_operator_Z7tw',
  // Not in the fixtures (no users/{uid} mirror): only their claims matter to the rules.
  managerA: 'uid_lojas_manager_R5st',
  operatorA: 'uid_lojas_operator_W2qa',
  noRole: 'uid_no_role_J8kd',
  unknownRole: 'uid_unknown_role_E3np',
  unverified: 'uid_unverified_op_H6vb',
  anonNoDevice: 'anonNoDevice01uidDDDDDDDDD',
  anonForged: 'anonForged01uidEEEEEEEEEEE',
} as const;

// ---------------------------------------------------------------- personas

interface Claims {
  role: string;
  org: string | null;
  sites: string[];
}

/** Claims as media-api sets them, read from the users/{uid} mirror fixture. */
function claimsFromUserFixture(uid: string): Claims {
  const users = loadFixtureFile('users.json');
  const user = userSchema.parse(users[`users/${uid}`]);
  return { role: user.role, org: user.orgId, sites: user.sites };
}

/** Real accounts are created verified (portal sign-up, or `emailVerified: true` via the Admin SDK). */
const password = (claims: Partial<Claims>, email: string, emailVerified = true): TokenOptions => ({
  ...claims,
  email,
  email_verified: emailVerified,
  firebase: { sign_in_provider: 'password' },
});

const anonymous = (extra: TokenOptions = {}): TokenOptions => ({
  ...extra,
  provider_id: 'anonymous',
  firebase: { sign_in_provider: 'anonymous' },
});

export const PERSONAS = {
  platform: { uid: UID.platform, token: password(claimsFromUserFixture(UID.platform), 'p@x.test') },
  adminA: { uid: UID.adminA, token: password(claimsFromUserFixture(UID.adminA), 'a@x.test') },
  adminB: { uid: UID.adminB, token: password(claimsFromUserFixture(UID.adminB), 'b@x.test') },
  managerA: {
    uid: UID.managerA,
    token: password({ role: 'manager', org: ORG_A, sites: [SITE_A] }, 'm@x.test'),
  },
  operatorA: {
    uid: UID.operatorA,
    token: password({ role: 'operator', org: ORG_A, sites: [SITE_A] }, 'oa@x.test'),
  },
  operatorB: {
    uid: UID.operatorB,
    token: password(claimsFromUserFixture(UID.operatorB), 'ob@x.test'),
  },
  /** Signed in with a real account, but media-api never granted a role. */
  noRole: { uid: UID.noRole, token: password({}, 'n@x.test') },
  /** A role the rules don't know. */
  unknownRole: {
    uid: UID.unknownRole,
    token: password({ role: 'superuser', org: ORG_A, sites: [SITE_A] }, 'u@x.test'),
  },
  /** A real operator claim, but the account's email is not verified. */
  unverified: {
    uid: UID.unverified,
    token: password({ role: 'operator', org: ORG_A, sites: [SITE_A] }, 'uv@x.test', false),
  },
  tvA: { uid: TV_A, token: anonymous() },
  tvB: { uid: TV_B, token: anonymous() },
  tvUnclaimed: { uid: TV_UNCLAIMED, token: anonymous() },
  /** Anonymous, and no devices/{uid} doc. */
  anonNoDevice: { uid: UID.anonNoDevice, token: anonymous() },
  /** Anonymous, carrying a staff claim: anonymous users never get a role. */
  anonForged: {
    uid: UID.anonForged,
    token: anonymous({ role: 'platformAdmin', org: ORG_A, sites: [SITE_A] }),
  },
} as const satisfies Record<string, { uid: string; token: TokenOptions }>;

export type Persona = keyof typeof PERSONAS | 'unauthed';

/** Everyone who is not staff or an operator of the org in question. */
export const OUTSIDERS = [
  'unauthed',
  'noRole',
  'unknownRole',
  'unverified',
  'anonNoDevice',
  'anonForged',
] as const;

// ---------------------------------------------------------------- codec (contract encoding <-> web SDK)

/** Contract encoding -> web SDK values (`{__ts}` -> Timestamp, `{__sentinel}` -> serverTimestamp()). */
export function toWeb(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(toWeb);
  if (value === null || typeof value !== 'object') return value;
  if (isServerTimestamp(value)) return serverTimestamp();
  if (isEncodedTimestamp(value)) {
    const { seconds, nanoseconds } = timestampParts(value);
    return new Timestamp(seconds, nanoseconds);
  }
  return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toWeb(v)]));
}

/** Web SDK values -> contract encoding. */
export function fromWeb(value: unknown): unknown {
  if (value instanceof Timestamp) return timestampFromParts(value.seconds, value.nanoseconds);
  if (Array.isArray(value)) return value.map(fromWeb);
  if (value === null || typeof value !== 'object') return value;
  return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, fromWeb(v)]));
}

// ---------------------------------------------------------------- environment

/** The compat instance from rules-unit-testing works with the modular API. */
const asDb = (ctx: RulesTestContext): Firestore => ctx.firestore() as unknown as Firestore;

export interface RulesEnv {
  env: RulesTestEnvironment;
  /** A Firestore handle signed in as `p`. */
  db(p: Persona): Firestore;
  /** Write docs with the rules off (contract encoding). */
  admin(fn: (db: Firestore) => Promise<void>): Promise<void>;
  /** Wipe the database and seed every contract fixture plus the Flutter-era legacy queues. */
  reset(): Promise<void>;
}

export async function createRulesEnv(): Promise<RulesEnv> {
  const env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: readFileSync(RULES_PATH, 'utf8') },
  });
  const seed = { ...loadContractFixtures(), ...loadLegacyQueueFixtures() };

  const admin = (fn: (db: Firestore) => Promise<void>) =>
    env.withSecurityRulesDisabled((ctx) => fn(asDb(ctx)));

  return {
    env,
    db: (p) => {
      if (p === 'unauthed') return asDb(env.unauthenticatedContext());
      const { uid, token } = PERSONAS[p];
      return asDb(env.authenticatedContext(uid, token));
    },
    admin,
    reset: async () => {
      await env.clearFirestore();
      await admin(async (db) => {
        const batch = writeBatch(db);
        for (const [path, data] of Object.entries(seed)) {
          batch.set(doc(db, path), toWeb(data) as Record<string, unknown>);
        }
        await batch.commit();
      });
    },
  };
}

/** The contract's claim transaction (portal A5) on the web SDK, as the portal will run it. */
export function webClaimRunner(db: Firestore): RunClaimTransaction {
  const read = async (t: Parameters<Parameters<typeof runTransaction>[1]>[0], path: string) => {
    const snap = await t.get(doc(db, path));
    return snap.exists() ? fromWeb(snap.data()) : null;
  };
  return (fn) =>
    runTransaction(db, (t) =>
      fn({
        getPairingCode: (code) => read(t, `pairingCodes/${code}`),
        getSite: (siteId) => read(t, `sites/${siteId}`),
        getScreen: (siteId, screenId) => read(t, `sites/${siteId}/screens/${screenId}`),
        getDevice: (uid) => read(t, `devices/${uid}`),
        updateDevice: (uid, patch) => {
          t.update(doc(db, `devices/${uid}`), toWeb(patch) as Record<string, unknown>);
        },
        deletePairingCode: (code) => {
          t.delete(doc(db, `pairingCodes/${code}`));
        },
      }),
    );
}
