import { Timestamp } from 'firebase-admin/firestore';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import {
  COLLECTIONS,
  COLLECTION_NAMES,
  isEncodedTimestamp,
  isServerTimestamp,
  parseDocPath,
  timestampFromParts,
  timestampParts,
} from '../../src/index.js';
import { fromFirestore, writeDocs } from '../../src/admin/index.js';
import { loadContractFixtures } from '../../src/fixtures.js';
import { clearFirestore, close, db, runScript } from './helpers.js';

const fixtures = loadContractFixtures();

/** Sentinel fields dropped and timestamps in canonical form, for comparing with what was stored. */
function withoutSentinels(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(withoutSentinels);
  if (value === null || typeof value !== 'object') return value;
  if (isEncodedTimestamp(value)) {
    const { seconds, nanoseconds } = timestampParts(value);
    return timestampFromParts(seconds, nanoseconds);
  }
  return Object.fromEntries(
    Object.entries(value)
      .filter(([, v]) => !isServerTimestamp(v))
      .map(([k, v]) => [k, withoutSentinels(v)]),
  );
}

beforeEach(clearFirestore);
afterAll(close);

describe('seed (emulator)', () => {
  it('stores every fixture so it reads back valid and equal', async () => {
    const result = await writeDocs(db, fixtures);
    expect(result.written).toBe(Object.keys(fixtures).length);

    for (const [path, fixture] of Object.entries(fixtures)) {
      const snap = await db.doc(path).get();
      expect(snap.exists, path).toBe(true);
      const stored = fromFirestore(snap.data()) as Record<string, unknown>;
      const { collection } = parseDocPath(path);
      const parsed = COLLECTIONS[collection].schema.safeParse(stored);
      expect(parsed.success, `${path}: ${parsed.success ? '' : parsed.error.message}`).toBe(true);
      expect(withoutSentinels(stored), path).toEqual(
        expect.objectContaining(withoutSentinels(fixture) as object),
      );
      // sentinels were resolved by the server
      for (const [k, v] of Object.entries(fixture as object)) {
        if (isServerTimestamp(v)) expect(snap.get(k), `${path}.${k}`).toBeInstanceOf(Timestamp);
      }
    }
  });

  it('keeps microsecond timestamps exact', async () => {
    await writeDocs(db, fixtures);
    const snap = await db.doc('devices/tvPortoLeft01uidAAAAAAAAAA').get();
    const t = snap.get('status.lastSyncAt') as Timestamp;
    expect(t.nanoseconds).toBe(123456000);
  });

  it('supports the TV read paths: queues by siteId, playlists org-wide or by site', async () => {
    await writeDocs(db, fixtures);
    const q = await db.collection('queues').where('siteId', '==', 'site_porto_3fKq').get();
    expect(q.size).toBe(2);
    const orgWide = await db
      .collection('playlists')
      .where('orgId', '==', 'org_lojas_7Hq2')
      .where('siteId', '==', null)
      .get();
    expect(orgWide.docs.map((d) => d.id)).toEqual(['pl_promo_nov_Zx81']);
    const usedIn = await db
      .collection('playlists')
      .where('mediaIds', 'array-contains', 'md_alps_5c0d')
      .get();
    expect(usedIn.docs.map((d) => d.id)).toEqual(['pl_saude_Rk42']);
  });

  it('the CLI seeds 2 orgs, each with a site, 2 screens, 2 queues and a playlist, plus an unclaimed box', async () => {
    const r = await runScript('seed.ts', []);
    expect(r.code, r.stderr).toBe(0);
    expect(r.stdout).toContain('Firestore emulator');
    for (const c of COLLECTION_NAMES.filter((c) => c !== 'screens')) {
      const expected = Object.keys(fixtures).filter((p) => parseDocPath(p).collection === c).length;
      expect((await db.collection(c).get()).size, c).toBe(expected);
    }
    const orgs = (await db.collection('orgs').get()).docs.map((d) => d.id);
    expect(orgs).toHaveLength(2);
    for (const org of orgs) {
      const sites = await db.collection('sites').where('orgId', '==', org).get();
      const site = sites.docs[0]!;
      expect((await site.ref.collection('screens').get()).size).toBe(2);
      expect((await db.collection('queues').where('siteId', '==', site.id).get()).size).toBe(2);
      expect(
        (await db.collection('playlists').where('orgId', '==', org).get()).size,
      ).toBeGreaterThan(0);
    }
    const unclaimed = (await db.collection('devices').get()).docs.filter((d) => !d.get('orgId'));
    expect(unclaimed).toHaveLength(1);
  });

  it('refuses the production project', async () => {
    const r = await runScript('seed.ts', ['--dev-project', 'santos-1b6f6']);
    expect(r.code).not.toBe(0);
  });
});
