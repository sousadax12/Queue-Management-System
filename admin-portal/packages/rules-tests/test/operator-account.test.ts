/**
 * End to end for `grant-operator` (issue #24): an account the contract's `grantOperator`
 * creates in the Auth emulator signs in with email and password (web SDK, real ID token from
 * the emulator, not a mocked one) and runs the operator's queue query against the real
 * `firestore.rules`.
 */
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { type GrantOperatorInput, connect, grantOperator } from '@qms/contract/admin';
import { loadFixtureFile } from '@qms/contract/fixtures';
import { type FirebaseApp, deleteApp, initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, signInWithEmailAndPassword } from 'firebase/auth';
import {
  type Firestore,
  collection,
  connectFirestoreEmulator,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  query,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import {
  ORG_A,
  ORG_B,
  PROJECT_ID,
  QUEUE_A,
  QUEUE_B,
  type RulesEnv,
  SITE_A,
  SITE_B,
  createRulesEnv,
  toWeb,
} from './env.js';

const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST ?? '';
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST;
if (!AUTH_HOST) {
  throw new Error('Run the rules tests with `pnpm test:emulator` (auth + firestore emulators).');
}

/** A second site of org A, with its own queue: same org, not the operator's site. */
const SITE_A2 = 'site_braga_T7vm';
const QUEUE_A2 = 'q_braga_balcao_Vb3n';
const PASSWORD = 'operator-pw-123';

const admin = connect({ kind: 'emulator', projectId: PROJECT_ID, host: FIRESTORE_HOST });

let t: RulesEnv;
const apps: FirebaseApp[] = [];

beforeAll(async () => {
  t = await createRulesEnv();
});
afterAll(async () => {
  await t.env.cleanup();
  await admin.close();
});
beforeEach(async () => {
  await t.reset();
  const res = await fetch(`http://${AUTH_HOST}/emulator/v1/projects/${PROJECT_ID}/accounts`, {
    method: 'DELETE',
  });
  if (!res.ok) throw new Error(`clear auth failed: ${String(res.status)}`);
  const site = loadFixtureFile('sites.json')[`sites/${SITE_A}`] as Record<string, unknown>;
  const queue = loadFixtureFile('queues.json')[`queues/${QUEUE_A}`] as Record<string, unknown>;
  await t.admin(async (db) => {
    await setDoc(doc(db, `sites/${SITE_A2}`), toWeb({ ...site, name: 'Loja Braga' }) as object);
    await setDoc(doc(db, `queues/${QUEUE_A2}`), toWeb({ ...queue, siteId: SITE_A2 }) as object);
  });
});
afterEach(async () => {
  await Promise.all(apps.splice(0).map((a) => deleteApp(a)));
});

/** Create the account with grant-operator, then sign in as it like the operator app does. */
async function grantAndSignIn(
  over: Partial<GrantOperatorInput> = {},
): Promise<{ db: Firestore; org: string; sites: string[] }> {
  const input: GrantOperatorInput = {
    email: 'porto@example.com',
    role: 'operator',
    org: ORG_A,
    sites: [SITE_A],
    password: () => Promise.resolve(PASSWORD),
    by: 'test',
    ...over,
  };
  const granted = await grantOperator(admin.auth, admin.db, input);
  expect(granted.created).toBe(true);

  const app = initializeApp(
    { projectId: PROJECT_ID, apiKey: 'demo-api-key' },
    `operator-${String(apps.length)}-${String(Date.now())}`,
  );
  apps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${AUTH_HOST}`, { disableWarnings: true });
  const db = getFirestore(app);
  const [host, port] = FIRESTORE_HOST.split(':');
  connectFirestoreEmulator(db, host ?? '127.0.0.1', Number(port));

  const cred = await signInWithEmailAndPassword(auth, input.email, PASSWORD);
  // The scope comes from the ID token's claims, as lib/auth/operator_scope.dart reads it.
  const token = await cred.user.getIdTokenResult();
  expect(token.claims.email_verified).toBe(true);
  return { db, org: token.claims.org as string, sites: token.claims.sites as string[] };
}

/** Ids of the seeded queues (contract fixtures + QUEUE_A2) on `sites`, sorted. */
const queuesOf = (sites: string[]): string[] =>
  [
    ...Object.entries(loadFixtureFile('queues.json'))
      .filter(([, q]) => sites.includes((q as { siteId: string }).siteId))
      .map(([path]) => path.slice('queues/'.length)),
    ...(sites.includes(SITE_A2) ? [QUEUE_A2] : []),
  ].sort();

/** The operator's queue query (operator_scope.dart: `orgId ==`, `siteId in`). */
const operatorQuery = (db: Firestore, org: string, sites: string[]) =>
  query(collection(db, 'queues'), where('orgId', '==', org), where('siteId', 'in', sites));

describe('an operator account created by grant-operator', () => {
  it('runs the operator queue query and moves `number` on its own site', async () => {
    const { db, org, sites } = await grantAndSignIn();
    expect({ org, sites }).toEqual({ org: ORG_A, sites: [SITE_A] });

    const snap = await assertSucceeds(getDocs(operatorQuery(db, org, sites)));
    expect(queuesOf([SITE_A])).toContain(QUEUE_A);
    expect(snap.docs.map((d) => d.id).sort()).toEqual(queuesOf([SITE_A]));

    await assertSucceeds(updateDoc(doc(db, `queues/${QUEUE_A}`), { number: 42 }));
    const after = await assertSucceeds(getDoc(doc(db, `queues/${QUEUE_A}`)));
    expect(after.get('number')).toBe(42);
    // Only `number`.
    await assertFails(updateDoc(doc(db, `queues/${QUEUE_A}`), { name: 'Renamed' }));
  });

  it("is denied another site's queue in the same org", async () => {
    const { db, org } = await grantAndSignIn();
    await assertFails(getDocs(operatorQuery(db, org, [SITE_A2])));
    await assertFails(getDocs(operatorQuery(db, org, [SITE_A, SITE_A2])));
    await assertFails(getDocs(query(collection(db, 'queues'), where('orgId', '==', org))));
    await assertFails(getDoc(doc(db, `queues/${QUEUE_A2}`)));
    await assertFails(updateDoc(doc(db, `queues/${QUEUE_A2}`), { number: 7 }));
  });

  it("is denied another org's queue", async () => {
    const { db } = await grantAndSignIn();
    await assertFails(getDocs(operatorQuery(db, ORG_B, [SITE_B])));
    await assertFails(getDoc(doc(db, `queues/${QUEUE_B}`)));
    await assertFails(updateDoc(doc(db, `queues/${QUEUE_B}`), { number: 7 }));
  });

  it('a manager of two sites sees and advances the queues of both', async () => {
    const { db, org, sites } = await grantAndSignIn({
      email: 'gerente@example.com',
      role: 'manager',
      sites: [SITE_A, SITE_A2],
    });
    const snap = await assertSucceeds(getDocs(operatorQuery(db, org, sites)));
    expect(snap.docs.map((d) => d.id).sort()).toEqual(queuesOf([SITE_A, SITE_A2]));
    await assertSucceeds(updateDoc(doc(db, `queues/${QUEUE_A2}`), { number: 3 }));
    await assertFails(getDoc(doc(db, `queues/${QUEUE_B}`)));
  });
});
