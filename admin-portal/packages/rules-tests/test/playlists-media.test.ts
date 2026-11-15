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
  MEDIA_B,
  ORG_A,
  ORG_B,
  PLAYLIST_A_ORGWIDE,
  PLAYLIST_B_SITE,
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

const playlist = (orgId: string, siteId: string | null) => ({
  orgId,
  siteId,
  name: 'New playlist',
  items: [],
  mediaIds: [],
});

describe('playlists', () => {
  it('a TV reads its org-wide and its site playlists only', async () => {
    await assertSucceeds(getDoc(doc(t.db('tvA'), `playlists/${PLAYLIST_A_ORGWIDE}`)));
    await assertSucceeds(getDoc(doc(t.db('tvB'), `playlists/${PLAYLIST_B_SITE}`)));
    await assertFails(getDoc(doc(t.db('tvB'), `playlists/${PLAYLIST_A_ORGWIDE}`)));
    await assertFails(getDoc(doc(t.db('tvA'), `playlists/${PLAYLIST_B_SITE}`)));
    await assertFails(getDoc(doc(t.db('tvUnclaimed'), `playlists/${PLAYLIST_A_ORGWIDE}`)));
  });

  it('staff and operators read their org playlists; not org B (cross-org)', async () => {
    for (const who of ['adminA', 'managerA', 'operatorA'] as const) {
      await assertSucceeds(getDoc(doc(t.db(who), `playlists/${PLAYLIST_A_ORGWIDE}`)));
      await assertFails(getDoc(doc(t.db(who), `playlists/${PLAYLIST_B_SITE}`)));
    }
    await assertSucceeds(getDoc(doc(t.db('operatorB'), `playlists/${PLAYLIST_B_SITE}`)));
    await assertSucceeds(
      getDocs(query(collection(t.db('adminA'), 'playlists'), where('orgId', '==', ORG_A))),
    );
    await assertFails(
      getDocs(query(collection(t.db('adminA'), 'playlists'), where('orgId', '==', ORG_B))),
    );
  });

  it('org-wide playlists are for org admins; site playlists for managers too', async () => {
    await assertSucceeds(setDoc(doc(t.db('adminA'), 'playlists/pl_1'), playlist(ORG_A, null)));
    await assertFails(setDoc(doc(t.db('managerA'), 'playlists/pl_2'), playlist(ORG_A, null)));
    await assertSucceeds(setDoc(doc(t.db('managerA'), 'playlists/pl_3'), playlist(ORG_A, SITE_A)));
    await assertSucceeds(updateDoc(doc(t.db('managerA'), 'playlists/pl_3'), { name: 'Renamed' }));
    await assertFails(updateDoc(doc(t.db('managerA'), 'playlists/pl_3'), { siteId: null }));
    await assertFails(updateDoc(doc(t.db('managerA'), 'playlists/pl_1'), { name: 'x' }));
    await assertSucceeds(deleteDoc(doc(t.db('managerA'), 'playlists/pl_3')));
    await assertFails(setDoc(doc(t.db('operatorA'), 'playlists/pl_4'), playlist(ORG_A, SITE_A)));
  });

  it('a playlist names its site explicitly (null = org-wide)', async () => {
    const noSite: Partial<ReturnType<typeof playlist>> = playlist(ORG_A, null);
    delete noSite.siteId;
    await assertFails(setDoc(doc(t.db('adminA'), 'playlists/pl_5'), noSite));
  });

  it('org A cannot put a playlist on org B, or edit org B playlists (cross-org)', async () => {
    const db = t.db('adminA');
    await assertFails(setDoc(doc(db, 'playlists/pl_evil_1'), playlist(ORG_B, SITE_B)));
    await assertFails(setDoc(doc(db, 'playlists/pl_evil_2'), playlist(ORG_A, SITE_B)));
    await assertFails(setDoc(doc(db, 'playlists/pl_evil_3'), playlist(ORG_B, null)));
    await assertFails(updateDoc(doc(db, `playlists/${PLAYLIST_B_SITE}`), { name: 'x' }));
    await assertFails(deleteDoc(doc(db, `playlists/${PLAYLIST_B_SITE}`)));
  });
});

describe('media', () => {
  it('staff of the org read media; operators and TVs never do', async () => {
    for (const who of ['platform', 'adminA', 'managerA'] as const) {
      await assertSucceeds(getDoc(doc(t.db(who), `media/${MEDIA_A}`)));
    }
    for (const who of ['operatorA', 'tvA'] as const) {
      await assertFails(getDoc(doc(t.db(who), `media/${MEDIA_A}`)));
    }
    await assertSucceeds(
      getDocs(query(collection(t.db('adminA'), 'media'), where('orgId', '==', ORG_A))),
    );
  });

  it('org A cannot read org B media (cross-org)', async () => {
    await assertFails(getDoc(doc(t.db('adminA'), `media/${MEDIA_B}`)));
    await assertFails(
      getDocs(query(collection(t.db('adminA'), 'media'), where('orgId', '==', ORG_B))),
    );
  });

  it('staff rename and tag media; everything else is media-api only', async () => {
    const db = t.db('adminA');
    await assertSucceeds(updateDoc(doc(db, `media/${MEDIA_A}`), { name: 'Bunny', tags: ['x'] }));
    await assertFails(updateDoc(doc(db, `media/${MEDIA_A}`), { status: 'failed' }));
    await assertFails(updateDoc(doc(db, `media/${MEDIA_A}`), { renditions: [] }));
    await assertFails(updateDoc(doc(db, `media/${MEDIA_B}`), { name: 'x' }));
    for (const who of ['platform', 'adminA'] as const) {
      await assertFails(setDoc(doc(t.db(who), 'media/md_new_1'), { orgId: ORG_A, siteId: null }));
      await assertFails(deleteDoc(doc(t.db(who), `media/${MEDIA_A}`)));
    }
  });
});
