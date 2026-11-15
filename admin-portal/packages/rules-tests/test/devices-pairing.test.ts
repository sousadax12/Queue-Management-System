import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { claimPairingCode } from '@qms/contract';
import {
  Timestamp,
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
  ORG_A,
  ORG_B,
  PAIRING_CODE,
  type Persona,
  type RulesEnv,
  SCREEN_A,
  SCREEN_B,
  SITE_A,
  SITE_B,
  TV_A,
  TV_B,
  TV_UNCLAIMED,
  UID,
  createRulesEnv,
  webClaimRunner,
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

const claim = (who: Persona, siteId: string, screenId: string, by: string) =>
  claimPairingCode(webClaimRunner(t.db(who)), { code: PAIRING_CODE, siteId, screenId, by });

const info = { model: 'Test box', sdkInt: 34, appVersion: '0.6.0 (21)', appVersionCode: 21 };

describe('TV box', () => {
  it('creates its own doc without assignment fields', async () => {
    const db = t.db('anonNoDevice');
    const me = `devices/${UID.anonNoDevice}`;
    await assertSucceeds(getDoc(doc(db, me)));
    await assertSucceeds(
      setDoc(doc(db, me), { info, status: { pendingItems: 0 }, lastSeenAt: serverTimestamp() }),
    );
    await assertSucceeds(updateDoc(doc(db, me), { lastSeenAt: serverTimestamp() }));
  });

  it.each(['orgId', 'siteId', 'screenId', 'name', 'identifyUntil', 'updatedBy'])(
    'cannot create its doc with %s',
    async (field) => {
      const db = t.db('anonNoDevice');
      await assertFails(setDoc(doc(db, `devices/${UID.anonNoDevice}`), { info, [field]: 'x' }));
    },
  );

  it("cannot create another uid's doc; staff cannot create device docs", async () => {
    await assertFails(setDoc(doc(t.db('anonNoDevice'), 'devices/someoneElse'), { info }));
    await assertFails(setDoc(doc(t.db('adminA'), `devices/${UID.adminA}`), { info }));
  });

  it('heartbeats, but cannot assign or rename itself', async () => {
    const db = t.db('tvA');
    await assertSucceeds(
      updateDoc(doc(db, `devices/${TV_A}`), {
        lastSeenAt: serverTimestamp(),
        'status.pendingItems': 1,
      }),
    );
    await assertFails(updateDoc(doc(db, `devices/${TV_A}`), { siteId: SITE_B }));
    await assertFails(updateDoc(doc(db, `devices/${TV_A}`), { name: 'x' }));
    await assertFails(
      updateDoc(doc(t.db('tvUnclaimed'), `devices/${TV_UNCLAIMED}`), { orgId: ORG_A }),
    );
  });

  it('reads its own doc only', async () => {
    await assertSucceeds(getDoc(doc(t.db('tvA'), `devices/${TV_A}`)));
    await assertFails(getDoc(doc(t.db('tvA'), `devices/${TV_B}`)));
    await assertFails(getDoc(doc(t.db('tvA'), `devices/${TV_UNCLAIMED}`)));
    await assertFails(getDocs(collection(t.db('tvA'), 'devices')));
  });

  it('cannot delete its doc', async () => {
    await assertFails(deleteDoc(doc(t.db('tvA'), `devices/${TV_A}`)));
  });
});

describe('pairing codes', () => {
  const code = (uid: string) => ({
    uid,
    createdAt: serverTimestamp(),
    expiresAt: Timestamp.fromMillis(Date.now() + 3_600_000),
  });

  it('a box creates a code for itself with the server time', async () => {
    const db = t.db('tvUnclaimed');
    await assertSucceeds(setDoc(doc(db, 'pairingCodes/ABCDEF'), code(TV_UNCLAIMED)));
    await assertFails(
      setDoc(doc(db, 'pairingCodes/ABCDEG'), {
        ...code(TV_UNCLAIMED),
        createdAt: Timestamp.fromMillis(Date.now() - 60_000),
      }),
    );
    await assertFails(setDoc(doc(db, 'pairingCodes/ABCDEH'), code(TV_A)));
    await assertFails(setDoc(doc(db, 'pairingCodes/ABCDEJ'), { ...code(TV_UNCLAIMED), x: 1 }));
    await assertFails(setDoc(doc(t.db('adminA'), 'pairingCodes/ABCDEK'), code(UID.adminA)));
  });

  it('set() on an existing code is denied: the collision signal (C4)', async () => {
    await assertFails(setDoc(doc(t.db('tvA'), `pairingCodes/${PAIRING_CODE}`), code(TV_A)));
  });

  it('boxes cannot read codes; staff get one by code but cannot list', async () => {
    await assertFails(getDoc(doc(t.db('tvUnclaimed'), `pairingCodes/${PAIRING_CODE}`)));
    await assertFails(getDoc(doc(t.db('operatorA'), `pairingCodes/${PAIRING_CODE}`)));
    for (const who of ['platform', 'adminA', 'adminB', 'managerA'] as const) {
      await assertSucceeds(getDoc(doc(t.db(who), `pairingCodes/${PAIRING_CODE}`)));
      await assertFails(getDocs(collection(t.db(who), 'pairingCodes')));
    }
  });

  it('the box deletes its own code; another box cannot', async () => {
    await assertFails(deleteDoc(doc(t.db('tvA'), `pairingCodes/${PAIRING_CODE}`)));
    await assertSucceeds(deleteDoc(doc(t.db('tvUnclaimed'), `pairingCodes/${PAIRING_CODE}`)));
  });
});

describe('claiming a box (contract claim transaction on the web SDK)', () => {
  it('an org admin claims an unclaimed box into its site', async () => {
    const r = await claim('adminA', SITE_A, SCREEN_A, UID.adminA);
    expect(r).toMatchObject({ uid: TV_UNCLAIMED, orgId: ORG_A, siteId: SITE_A, moved: false });
    // The box now reads its site.
    await assertSucceeds(getDoc(doc(t.db('tvUnclaimed'), `sites/${SITE_A}`)));
    const used = await getDoc(doc(t.db('adminA'), `pairingCodes/${PAIRING_CODE}`));
    expect(used.exists()).toBe(false);
  });

  it('a manager claims into its own site, not into another site of its org', async () => {
    await t.admin(async (db) => {
      await setDoc(doc(db, 'sites/site_porto_norte_Q1'), { orgId: ORG_A, name: 'Norte' });
      await setDoc(doc(db, 'sites/site_porto_norte_Q1/screens/scr_n_1'), {
        orgId: ORG_A,
        name: 'N',
      });
    });
    await assertFails(claim('managerA', 'site_porto_norte_Q1', 'scr_n_1', UID.managerA));
    await assertSucceeds(claim('managerA', SITE_A, SCREEN_A, UID.managerA));
  });

  it('operators cannot claim', async () => {
    await assertFails(claim('operatorA', SITE_A, SCREEN_A, UID.operatorA));
  });

  it('org A cannot claim into org B (cross-org)', async () => {
    await assertFails(claim('adminA', SITE_B, SCREEN_B, UID.adminA));
  });

  it('org A cannot claim into org B by writing the device directly (cross-org)', async () => {
    const db = t.db('adminA');
    const ref = doc(db, `devices/${TV_UNCLAIMED}`);
    // Labelled as org A but pointing at org B's site: would let the box read org B's data.
    await assertFails(updateDoc(ref, { orgId: ORG_A, siteId: SITE_B, screenId: SCREEN_B }));
    await assertFails(updateDoc(ref, { orgId: ORG_A, siteId: SITE_B, screenId: null }));
    await assertFails(updateDoc(ref, { orgId: ORG_B, siteId: SITE_B, screenId: SCREEN_B }));
    // A screen of another site, even with its own site.
    await assertFails(updateDoc(ref, { orgId: ORG_A, siteId: SITE_A, screenId: SCREEN_B }));
    // A site that does not exist.
    await assertFails(updateDoc(ref, { orgId: ORG_A, siteId: 'site_ghost_1', screenId: null }));
    // Control: the same write into its own site works.
    await assertSucceeds(updateDoc(ref, { orgId: ORG_A, siteId: SITE_A, screenId: SCREEN_A }));
  });

  it("org B cannot take, read or unassign org A's box (cross-org)", async () => {
    const db = t.db('adminB');
    const ref = doc(db, `devices/${TV_A}`);
    await assertFails(getDoc(ref));
    await assertFails(updateDoc(ref, { orgId: ORG_B, siteId: SITE_B, screenId: SCREEN_B }));
    await assertFails(updateDoc(ref, { orgId: null, siteId: null, screenId: null }));
    await assertFails(updateDoc(ref, { name: 'mine now' }));
  });

  it('a box already in org B cannot be re-claimed by org A through a new code', async () => {
    await t.admin(async (db) => {
      await updateDoc(doc(db, `pairingCodes/${PAIRING_CODE}`), { uid: TV_B });
    });
    await assertFails(claim('adminA', SITE_A, SCREEN_A, UID.adminA));
  });

  it('org admins rename, identify, move within the org and unassign their boxes', async () => {
    const db = t.db('adminA');
    const ref = doc(db, `devices/${TV_A}`);
    await assertSucceeds(
      updateDoc(ref, { name: 'Box 3', identifyUntil: Timestamp.fromMillis(Date.now() + 60_000) }),
    );
    await assertSucceeds(updateDoc(ref, { screenId: 'scr_lobby_right_Vb8n' }));
    await assertSucceeds(updateDoc(ref, { siteId: null, screenId: null }));
    await assertSucceeds(updateDoc(ref, { orgId: null }));
  });

  it('only platform admins move boxes across orgs or delete them', async () => {
    const move = { orgId: ORG_B, siteId: SITE_B, screenId: SCREEN_B };
    await assertFails(updateDoc(doc(t.db('adminA'), `devices/${TV_A}`), move));
    await assertSucceeds(updateDoc(doc(t.db('platform'), `devices/${TV_A}`), move));
    await assertFails(deleteDoc(doc(t.db('adminB'), `devices/${TV_A}`)));
    await assertSucceeds(deleteDoc(doc(t.db('platform'), `devices/${TV_A}`)));
  });

  it('staff cannot touch the box-owned fields', async () => {
    await assertFails(updateDoc(doc(t.db('adminA'), `devices/${TV_A}`), { status: {} }));
    await assertFails(updateDoc(doc(t.db('adminA'), `devices/${TV_A}`), { lastSeenAt: null }));
  });
});

describe('fleet table', () => {
  it('org admins and managers list the boxes they manage only', async () => {
    const devices = (who: Persona) => collection(t.db(who), 'devices');
    const a = await assertSucceeds(getDocs(query(devices('adminA'), where('orgId', '==', ORG_A))));
    expect(a.docs.map((d) => d.id)).toEqual([TV_A]);
    await assertFails(getDocs(query(devices('adminA'), where('orgId', '==', ORG_B))));
    await assertFails(getDocs(devices('adminA')));
    await assertSucceeds(
      getDocs(
        query(devices('managerA'), where('orgId', '==', ORG_A), where('siteId', '==', SITE_A)),
      ),
    );
    await assertFails(getDocs(query(devices('managerA'), where('orgId', '==', ORG_A))));
    await assertFails(getDocs(query(devices('operatorA'), where('orgId', '==', ORG_A))));
    const all = await assertSucceeds(getDocs(devices('platform')));
    expect(all.size).toBe(3);
  });

  it('any staff gets an unclaimed box by uid (from a code), not a claimed one of another org', async () => {
    await assertSucceeds(getDoc(doc(t.db('adminB'), `devices/${TV_UNCLAIMED}`)));
    await assertSucceeds(getDoc(doc(t.db('managerA'), `devices/${TV_UNCLAIMED}`)));
    await assertFails(getDoc(doc(t.db('operatorA'), `devices/${TV_UNCLAIMED}`)));
    await assertFails(getDoc(doc(t.db('adminB'), `devices/${TV_A}`)));
  });
});
