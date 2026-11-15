import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
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
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import {
  ORG_A,
  ORG_B,
  type RulesEnv,
  SCREEN_A,
  SCREEN_B,
  SITE_A,
  SITE_B,
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

const screen = (orgId: string) => ({
  orgId,
  name: 'New screen',
  template: 'queues_only',
  zones: {},
  updatedAt: serverTimestamp(),
});

describe('orgs', () => {
  it('every role reads its own org; platform admins read all', async () => {
    for (const who of ['adminA', 'managerA', 'operatorA'] as const) {
      await assertSucceeds(getDoc(doc(t.db(who), `orgs/${ORG_A}`)));
      await assertFails(getDoc(doc(t.db(who), `orgs/${ORG_B}`)));
    }
    await assertSucceeds(getDoc(doc(t.db('platform'), `orgs/${ORG_B}`)));
    await assertSucceeds(getDocs(collection(t.db('platform'), 'orgs')));
    await assertFails(getDocs(collection(t.db('adminA'), 'orgs')));
  });

  it('TVs never read orgs (C10)', async () => {
    await assertFails(getDoc(doc(t.db('tvA'), `orgs/${ORG_A}`)));
  });

  it('only platform admins create, delete or change limits', async () => {
    await assertSucceeds(setDoc(doc(t.db('platform'), 'orgs/org_new_1'), { name: 'New' }));
    await assertSucceeds(updateDoc(doc(t.db('platform'), `orgs/${ORG_A}`), { maxSites: 20 }));
    await assertSucceeds(deleteDoc(doc(t.db('platform'), 'orgs/org_new_1')));
    await assertFails(setDoc(doc(t.db('adminA'), 'orgs/org_new_2'), { name: 'New' }));
    await assertFails(updateDoc(doc(t.db('adminA'), `orgs/${ORG_A}`), { maxSites: 99 }));
    await assertFails(updateDoc(doc(t.db('adminA'), `orgs/${ORG_A}`), { status: 'suspended' }));
    await assertFails(deleteDoc(doc(t.db('adminA'), `orgs/${ORG_A}`)));
  });

  it('an org admin renames its own org only', async () => {
    const rename = { name: 'Lojas SA', updatedAt: serverTimestamp(), updatedBy: UID.adminA };
    await assertSucceeds(updateDoc(doc(t.db('adminA'), `orgs/${ORG_A}`), rename));
    await assertFails(updateDoc(doc(t.db('adminA'), `orgs/${ORG_B}`), rename));
    await assertFails(updateDoc(doc(t.db('adminA'), `orgs/${ORG_A}`), { name: '' }));
    await assertFails(updateDoc(doc(t.db('managerA'), `orgs/${ORG_A}`), rename));
  });
});

describe('sites', () => {
  it('staff and operators read the sites they act on; not org B (cross-org)', async () => {
    for (const who of ['platform', 'adminA', 'managerA', 'operatorA'] as const) {
      await assertSucceeds(getDoc(doc(t.db(who), `sites/${SITE_A}`)));
    }
    for (const who of ['adminA', 'managerA', 'operatorA'] as const) {
      await assertFails(getDoc(doc(t.db(who), `sites/${SITE_B}`)));
    }
    await assertSucceeds(
      getDocs(query(collection(t.db('adminA'), 'sites'), where('orgId', '==', ORG_A))),
    );
    await assertFails(
      getDocs(query(collection(t.db('adminA'), 'sites'), where('orgId', '==', ORG_B))),
    );
  });

  it('a TV reads its own site only', async () => {
    await assertSucceeds(getDoc(doc(t.db('tvA'), `sites/${SITE_A}`)));
    await assertFails(getDoc(doc(t.db('tvA'), `sites/${SITE_B}`)));
    await assertFails(getDoc(doc(t.db('tvUnclaimed'), `sites/${SITE_A}`)));
  });

  it('org admins create sites in their own org only', async () => {
    const site = (orgId: string) => ({ orgId, name: 'Loja Nova' });
    await assertSucceeds(setDoc(doc(t.db('adminA'), 'sites/site_new_1'), site(ORG_A)));
    await assertFails(setDoc(doc(t.db('adminA'), 'sites/site_new_2'), site(ORG_B)));
    await assertFails(setDoc(doc(t.db('adminA'), 'sites/site_new_3'), { name: 'No org' }));
    await assertFails(setDoc(doc(t.db('managerA'), 'sites/site_new_4'), site(ORG_A)));
  });

  it('managers edit their site but cannot move it to another org or delete it', async () => {
    const db = t.db('managerA');
    await assertSucceeds(updateDoc(doc(db, `sites/${SITE_A}`), { name: 'Porto' }));
    await assertFails(updateDoc(doc(db, `sites/${SITE_A}`), { orgId: ORG_B }));
    await assertFails(deleteDoc(doc(db, `sites/${SITE_A}`)));
    await assertFails(updateDoc(doc(t.db('operatorA'), `sites/${SITE_A}`), { name: 'x' }));
    await assertSucceeds(deleteDoc(doc(t.db('adminA'), `sites/${SITE_A}`)));
  });

  it('org A cannot edit or delete org B sites (cross-org)', async () => {
    await assertFails(updateDoc(doc(t.db('adminA'), `sites/${SITE_B}`), { name: 'x' }));
    await assertFails(deleteDoc(doc(t.db('adminA'), `sites/${SITE_B}`)));
  });
});

describe('screens', () => {
  it('a TV reads the screens of its site only', async () => {
    await assertSucceeds(getDoc(doc(t.db('tvA'), `sites/${SITE_A}/screens/${SCREEN_A}`)));
    await assertSucceeds(getDocs(collection(t.db('tvA'), `sites/${SITE_A}/screens`)));
    await assertFails(getDoc(doc(t.db('tvA'), `sites/${SITE_B}/screens/${SCREEN_B}`)));
  });

  it('operators read, managers write, in their own site', async () => {
    await assertSucceeds(getDoc(doc(t.db('operatorA'), `sites/${SITE_A}/screens/${SCREEN_A}`)));
    await assertFails(
      setDoc(doc(t.db('operatorA'), `sites/${SITE_A}/screens/scr_new_1`), screen(ORG_A)),
    );
    const db = t.db('managerA');
    await assertSucceeds(setDoc(doc(db, `sites/${SITE_A}/screens/scr_new_1`), screen(ORG_A)));
    await assertSucceeds(updateDoc(doc(db, `sites/${SITE_A}/screens/scr_new_1`), { name: 'L' }));
    await assertFails(updateDoc(doc(db, `sites/${SITE_A}/screens/scr_new_1`), { orgId: ORG_B }));
    await assertSucceeds(deleteDoc(doc(db, `sites/${SITE_A}/screens/scr_new_1`)));
  });

  it("org A cannot add a screen under org B's site, whatever orgId it claims (cross-org)", async () => {
    const db = t.db('adminA');
    await assertFails(setDoc(doc(db, `sites/${SITE_B}/screens/scr_evil_1`), screen(ORG_A)));
    await assertFails(setDoc(doc(db, `sites/${SITE_B}/screens/scr_evil_2`), screen(ORG_B)));
    await assertFails(getDoc(doc(db, `sites/${SITE_B}/screens/${SCREEN_B}`)));
    await assertFails(updateDoc(doc(db, `sites/${SITE_B}/screens/${SCREEN_B}`), { name: 'x' }));
  });
});

describe('users (claims mirror)', () => {
  it('users read their own mirror', async () => {
    await assertSucceeds(getDoc(doc(t.db('operatorB'), `users/${UID.operatorB}`)));
    await assertFails(getDoc(doc(t.db('operatorB'), `users/${UID.adminB}`)));
  });

  it('org admins read their org users only (cross-org)', async () => {
    await assertSucceeds(getDoc(doc(t.db('adminB'), `users/${UID.operatorB}`)));
    await assertSucceeds(
      getDocs(query(collection(t.db('adminB'), 'users'), where('orgId', '==', ORG_B))),
    );
    await assertFails(getDoc(doc(t.db('adminA'), `users/${UID.operatorB}`)));
    await assertFails(
      getDocs(query(collection(t.db('adminA'), 'users'), where('orgId', '==', ORG_B))),
    );
    await assertSucceeds(getDoc(doc(t.db('platform'), `users/${UID.operatorB}`)));
  });

  it('nobody writes the mirror from a client (media-api only)', async () => {
    for (const who of ['platform', 'adminA', 'operatorB'] as const) {
      await assertFails(
        updateDoc(doc(t.db(who), `users/${UID.operatorB}`), { role: 'orgAdmin', sites: [] }),
      );
    }
    await assertFails(
      setDoc(doc(t.db('adminA'), `users/${UID.adminA}`), { role: 'platformAdmin', orgId: null }),
    );
  });
});
