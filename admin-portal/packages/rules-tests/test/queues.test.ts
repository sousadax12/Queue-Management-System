import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { QUEUE_DEFAULTS } from '@qms/contract';
import {
  type Firestore,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import {
  LEGACY_QUEUE,
  ORG_A,
  ORG_B,
  QUEUE_A,
  QUEUE_B,
  type RulesEnv,
  SITE_A,
  SITE_B,
  createRulesEnv,
} from './env.js';

let t: RulesEnv;
beforeAll(async () => {
  t = await createRulesEnv();
});
afterAll(async () => {
  await t.env.cleanup();
});
beforeEach(async () => {
  await t.reset();
});

const newQueue = (orgId: string, siteId: string) => ({
  ...QUEUE_DEFAULTS,
  orgId,
  siteId,
  name: 'Balcão C',
  updatedAt: serverTimestamp(),
  updatedBy: 'someone',
});

/** What the Flutter operator app does (lib/providers/queues_provider.dart). */
const advance = (db: Firestore, queueId: string, number: unknown) =>
  updateDoc(doc(db, `queues/${queueId}`), { number });

/** The Flutter operator's query (lib/auth/operator_scope.dart): its org and its sites. */
const operatorQuery = (db: Firestore, orgId: string, sites: string[]) =>
  query(collection(db, 'queues'), where('orgId', '==', orgId), where('siteId', 'in', sites));

describe('operator (the Flutter app)', () => {
  it('advances a queue of its site', async () => {
    await assertSucceeds(advance(t.db('operatorA'), QUEUE_A, 43));
    await assertSucceeds(advance(t.db('operatorA'), QUEUE_A, 0));
    await assertSucceeds(advance(t.db('operatorA'), QUEUE_A, 99));
  });

  it('keeps number an int in 0..99', async () => {
    const db = t.db('operatorA');
    await assertFails(advance(db, QUEUE_A, 100));
    await assertFails(advance(db, QUEUE_A, -1));
    await assertFails(advance(db, QUEUE_A, 4.5));
    await assertFails(advance(db, QUEUE_A, '5'));
    await assertFails(advance(db, QUEUE_A, null));
  });

  it('changes nothing but number', async () => {
    const db = t.db('operatorA');
    await assertFails(updateDoc(doc(db, `queues/${QUEUE_A}`), { number: 1, name: 'x' }));
    await assertFails(updateDoc(doc(db, `queues/${QUEUE_A}`), { siteId: SITE_B }));
    await assertFails(deleteDoc(doc(db, `queues/${QUEUE_A}`)));
    await assertFails(setDoc(doc(db, 'queues/q_new_1'), newQueue(ORG_A, SITE_A)));
  });

  it('lists the queues of its sites with the app query', async () => {
    const snap = await assertSucceeds(getDocs(operatorQuery(t.db('operatorA'), ORG_A, [SITE_A])));
    expect(snap.docs.map((d) => d.id).sort()).toEqual(['q_balcao_a_P4tc', 'q_balcao_b_Lr6w']);
    await assertSucceeds(getDocs(operatorQuery(t.db('operatorB'), ORG_B, [SITE_B])));
  });

  it('cannot list without the org and site filters', async () => {
    const db = t.db('operatorA');
    await assertFails(getDocs(collection(db, 'queues')));
    await assertFails(getDocs(query(collection(db, 'queues'), where('orgId', '==', ORG_A))));
    await assertFails(getDocs(query(collection(db, 'queues'), where('siteId', '==', SITE_A))));
  });

  it('cannot see or advance queues of org B (cross-org)', async () => {
    const db = t.db('operatorA');
    await assertFails(getDoc(doc(db, `queues/${QUEUE_B}`)));
    await assertFails(advance(db, QUEUE_B, 1));
    await assertFails(getDocs(operatorQuery(db, ORG_B, [SITE_B])));
    await assertFails(getDocs(operatorQuery(db, ORG_A, [SITE_A, SITE_B])));
  });

  it('cannot see a site of its own org that is not in its claims', async () => {
    const otherSite = 'site_porto_norte_Q1';
    await t.admin(async (db) => {
      await setDoc(doc(db, 'queues/q_norte_1'), newQueue(ORG_A, otherSite));
    });
    await assertFails(getDoc(doc(t.db('operatorA'), 'queues/q_norte_1')));
    await assertFails(advance(t.db('operatorA'), 'q_norte_1', 1));
  });

  it('does not see Flutter-era queues until they are backfilled', async () => {
    await assertFails(getDoc(doc(t.db('operatorA'), `queues/${LEGACY_QUEUE}`)));
    await assertFails(advance(t.db('operatorA'), LEGACY_QUEUE, 13));
    await t.admin(async (db) => {
      await updateDoc(doc(db, `queues/${LEGACY_QUEUE}`), { orgId: ORG_A, siteId: SITE_A });
    });
    await assertSucceeds(advance(t.db('operatorA'), LEGACY_QUEUE, 13));
  });
});

/**
 * What the native operator app does (android-tv `:operator`, issue #26): the same scoped query,
 * then a plain `update({number})` computed from the last known value with the 99↔0 wrap. Not a
 * transaction, so it also works offline; the rules see one write per press.
 */
describe('operator (the native app)', () => {
  const step = (n: number, up: boolean) => (up ? (n >= 99 ? 0 : n + 1) : n <= 0 ? 99 : n - 1);

  /** One D-pad press: read the last known number from the scoped query, write the next one. */
  const press = async (db: Firestore, queueId: string, up: boolean) => {
    const snap = await getDocs(operatorQuery(db, ORG_A, [SITE_A]));
    const queue = snap.docs.find((d) => d.id === queueId);
    if (!queue) throw new Error(`${queueId} is not in the operator's query`);
    const next = step(queue.get('number') as number, up);
    await assertSucceeds(updateDoc(doc(db, `queues/${queueId}`), { number: next }));
    return next;
  };

  const numberOf = async (queueId: string) => {
    let n: unknown;
    await t.admin(async (db) => {
      n = (await getDoc(doc(db, `queues/${queueId}`))).get('number');
    });
    return n;
  };

  it('steps a queue of its site up and down from the last known value', async () => {
    const db = t.db('operatorA');
    expect(await press(db, QUEUE_A, true)).toBe(43);
    expect(await press(db, QUEUE_A, false)).toBe(42);
    expect(await press(db, QUEUE_A, false)).toBe(41);
    expect(await numberOf(QUEUE_A)).toBe(41);
  });

  it('wraps 99 → 0 and 0 → 99', async () => {
    const db = t.db('operatorA');
    await assertSucceeds(advance(db, QUEUE_A, 99));
    expect(await press(db, QUEUE_A, true)).toBe(0);
    expect(await numberOf(QUEUE_A)).toBe(0);
    expect(await press(db, QUEUE_A, false)).toBe(99);
    expect(await numberOf(QUEUE_A)).toBe(99);
  });

  it('writes nothing but number', async () => {
    const db = t.db('operatorA');
    await assertFails(updateDoc(doc(db, `queues/${QUEUE_A}`), { number: 1, soundVolume: 0 }));
    await assertFails(updateDoc(doc(db, `queues/${QUEUE_A}`), { number: 1, updatedBy: 'x' }));
  });

  it('is denied on a queue of another site of its own org', async () => {
    const otherSite = 'site_porto_norte_Q1';
    await t.admin(async (db) => {
      await setDoc(doc(db, 'queues/q_norte_1'), { ...newQueue(ORG_A, otherSite), number: 99 });
    });
    await assertFails(advance(t.db('operatorA'), 'q_norte_1', 0));
    await assertFails(getDocs(operatorQuery(t.db('operatorA'), ORG_A, [otherSite])));
  });

  it('is denied on a queue of another org', async () => {
    await assertFails(advance(t.db('operatorA'), QUEUE_B, 1));
  });
});

describe('TV device', () => {
  it('reads the queues of its own site', async () => {
    const db = t.db('tvA');
    await assertSucceeds(getDoc(doc(db, `queues/${QUEUE_A}`)));
    const snap = await assertSucceeds(
      getDocs(query(collection(db, 'queues'), where('siteId', '==', SITE_A))),
    );
    expect(snap.size).toBe(2);
  });

  it('cannot read queues of another site or org, or list all', async () => {
    const db = t.db('tvA');
    await assertFails(getDoc(doc(db, `queues/${QUEUE_B}`)));
    await assertFails(getDocs(query(collection(db, 'queues'), where('siteId', '==', SITE_B))));
    await assertFails(getDocs(collection(db, 'queues')));
  });

  it('cannot write queues', async () => {
    await assertFails(advance(t.db('tvA'), QUEUE_A, 1));
  });

  it('an unclaimed box reads no queue', async () => {
    await assertFails(getDoc(doc(t.db('tvUnclaimed'), `queues/${QUEUE_A}`)));
  });
});

describe('manager and org admin', () => {
  it.each(['managerA', 'adminA', 'platform'] as const)(
    '%s creates, edits and deletes a queue in site A',
    async (who) => {
      const db = t.db(who);
      await assertSucceeds(setDoc(doc(db, 'queues/q_new_1'), newQueue(ORG_A, SITE_A)));
      await assertSucceeds(updateDoc(doc(db, 'queues/q_new_1'), { name: 'Renamed', number: 5 }));
      await assertSucceeds(advance(db, 'q_new_1', 6));
      await assertSucceeds(deleteDoc(doc(db, 'queues/q_new_1')));
    },
  );

  it('a new queue needs every legacy field the Flutter mapper reads', async () => {
    const db = t.db('adminA');
    for (const field of ['number', 'icon', 'name', 'backgroundColor', 'videoURL', 'velocity']) {
      const q: Record<string, unknown> = newQueue(ORG_A, SITE_A);
      delete q[field];
      await assertFails(setDoc(doc(db, `queues/q_missing_${field}`), q));
    }
    await assertFails(
      setDoc(doc(db, 'queues/q_bad_1'), { ...newQueue(ORG_A, SITE_A), soundVolume: 0.5 }),
    );
    await assertFails(
      setDoc(doc(db, 'queues/q_bad_2'), { ...newQueue(ORG_A, SITE_A), extra: true }),
    );
  });

  it('cannot move a queue to another site or org', async () => {
    const db = t.db('adminA');
    await assertFails(updateDoc(doc(db, `queues/${QUEUE_A}`), { siteId: 'site_other_1' }));
    await assertFails(updateDoc(doc(db, `queues/${QUEUE_A}`), { orgId: ORG_B, siteId: SITE_B }));
  });

  it('org A cannot create a queue in org B, even labelled as org A (cross-org)', async () => {
    for (const who of ['adminA', 'managerA'] as const) {
      const db = t.db(who);
      await assertFails(setDoc(doc(db, 'queues/q_evil_1'), newQueue(ORG_B, SITE_B)));
      await assertFails(setDoc(doc(db, 'queues/q_evil_2'), newQueue(ORG_A, SITE_B)));
    }
  });

  it('org A cannot read, edit or delete org B queues (cross-org)', async () => {
    for (const who of ['adminA', 'managerA'] as const) {
      const db = t.db(who);
      await assertFails(getDoc(doc(db, `queues/${QUEUE_B}`)));
      await assertFails(updateDoc(doc(db, `queues/${QUEUE_B}`), { name: 'x' }));
      await assertFails(deleteDoc(doc(db, `queues/${QUEUE_B}`)));
    }
  });

  it('a manager cannot touch sites outside its claims', async () => {
    const otherSite = 'site_porto_norte_Q1';
    await t.admin(async (db) => {
      await setDoc(doc(db, `sites/${otherSite}`), { orgId: ORG_A, name: 'Porto Norte' });
    });
    await assertFails(setDoc(doc(t.db('managerA'), 'queues/q_new_2'), newQueue(ORG_A, otherSite)));
    await assertSucceeds(setDoc(doc(t.db('adminA'), 'queues/q_new_2'), newQueue(ORG_A, otherSite)));
  });

  it('org admins list their org; platform admins list everything, legacy included', async () => {
    await assertSucceeds(
      getDocs(query(collection(t.db('adminA'), 'queues'), where('orgId', '==', ORG_A))),
    );
    await assertFails(
      getDocs(query(collection(t.db('adminA'), 'queues'), where('orgId', '==', ORG_B))),
    );
    const all = await assertSucceeds(getDocs(collection(t.db('platform'), 'queues')));
    expect(all.size).toBe(7);
  });
});
