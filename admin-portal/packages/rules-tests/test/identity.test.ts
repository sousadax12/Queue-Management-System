import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import {
  MEDIA_A,
  ORG_A,
  OUTSIDERS,
  PAIRING_CODE,
  PLAYLIST_A_ORGWIDE,
  QUEUE_A,
  type RulesEnv,
  SCREEN_A,
  SITE_A,
  TV_A,
  UID,
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

const SEEDED = [
  `orgs/${ORG_A}`,
  `sites/${SITE_A}`,
  `sites/${SITE_A}/screens/${SCREEN_A}`,
  `queues/${QUEUE_A}`,
  `playlists/${PLAYLIST_A_ORGWIDE}`,
  `media/${MEDIA_A}`,
  `devices/${TV_A}`,
  `pairingCodes/${PAIRING_CODE}`,
  `users/${UID.adminA}`,
];

// Unauthenticated, signed in without a role, an unknown role, a role on an unverified email,
// anonymous without a device, and anonymous with a forged staff claim: none of them can read
// or write anything.
describe.each(OUTSIDERS)('%s', (who) => {
  it.each(SEEDED)('cannot read %s', async (path) => {
    await assertFails(getDoc(doc(t.db(who), path)));
  });

  it.each(['orgs', 'sites', 'queues', 'playlists', 'media', 'devices', 'users'])(
    'cannot list %s',
    async (c) => {
      await assertFails(getDocs(collection(t.db(who), c)));
    },
  );

  it('cannot list the queues of a site', async () => {
    const db = t.db(who);
    await assertFails(
      getDocs(
        query(collection(db, 'queues'), where('orgId', '==', ORG_A), where('siteId', '==', SITE_A)),
      ),
    );
  });

  it('cannot advance a queue number', async () => {
    await assertFails(updateDoc(doc(t.db(who), `queues/${QUEUE_A}`), { number: 43 }));
  });

  it('cannot create or delete a site', async () => {
    const db = t.db(who);
    await assertFails(setDoc(doc(db, 'sites/site_new_X1'), { orgId: ORG_A, name: 'New' }));
    await assertFails(deleteDoc(doc(db, `sites/${SITE_A}`)));
  });

  it('cannot create an org', async () => {
    await assertFails(setDoc(doc(t.db(who), 'orgs/org_new_X1'), { name: 'New', status: 'active' }));
  });

  it('cannot claim a box', async () => {
    await assertFails(
      updateDoc(doc(t.db(who), 'devices/tvUnclaimed01uidCCCCCCCCCC'), {
        orgId: ORG_A,
        siteId: SITE_A,
        screenId: SCREEN_A,
        name: 'mine',
      }),
    );
  });
});

describe('a known role in the token', () => {
  it('is what grants access (control for the outsider cases)', async () => {
    await assertSucceeds(getDoc(doc(t.db('operatorA'), `queues/${QUEUE_A}`)));
  });

  it('only counts on a verified email: the same operator claim, unverified, is denied', async () => {
    await assertSucceeds(updateDoc(doc(t.db('operatorA'), `queues/${QUEUE_A}`), { number: 44 }));
    await assertFails(updateDoc(doc(t.db('unverified'), `queues/${QUEUE_A}`), { number: 45 }));
    await assertFails(getDoc(doc(t.db('unverified'), `queues/${QUEUE_A}`)));
  });
});
