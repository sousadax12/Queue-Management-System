import { describe, expect, it } from 'vitest';
import {
  COLLECTIONS,
  COLLECTION_NAMES,
  checkReferences,
  isClaimed,
  parseDocPath,
  queueReadSchema,
  queueSchema,
  resolveSentinels,
  screenSchema,
  ts,
  zonesOutsideTemplate,
  type DeviceDoc,
  type PlaylistDoc,
  type QueueDoc,
  type ScreenDoc,
  type SiteDoc,
} from '../src/index.js';
import {
  loadCollectionFixtures,
  loadContractFixtures,
  loadLegacyQueueFixtures,
  loadTemplates,
} from '../src/fixtures.js';

const all = loadContractFixtures();
const byCollection = (c: string) =>
  Object.entries(all).filter(([p]) => parseDocPath(p).collection === c);

describe('golden fixtures', () => {
  it.each(COLLECTION_NAMES)('%s.json validates against its schema', (collection) => {
    const docs = loadCollectionFixtures(collection);
    expect(Object.keys(docs).length).toBeGreaterThan(0);
    for (const [path, doc] of Object.entries(docs)) {
      const r = COLLECTIONS[collection].schema.safeParse(doc);
      expect(r.success, `${path}: ${r.success ? '' : r.error.message}`).toBe(true);
    }
  });

  it('still validates once serverTimestamp sentinels are resolved (what a reader sees)', () => {
    const now = ts(new Date('2026-11-15T10:00:00Z'));
    for (const [path, doc] of Object.entries(all)) {
      const { collection } = parseDocPath(path);
      const r = COLLECTIONS[collection].schema.safeParse(resolveSentinels(doc, now));
      expect(r.success, path).toBe(true);
    }
  });

  it('is referentially consistent (orgs, sites, screens, queues, playlists, devices)', () => {
    expect(checkReferences(all)).toEqual([]);
  });

  it('covers what the seed promises: 2 orgs, each with a site, 2 screens, 2 queues and a playlist', () => {
    const orgs = byCollection('orgs').map(([p]) => parseDocPath(p).id);
    expect(orgs).toHaveLength(2);
    for (const org of orgs) {
      const sites = byCollection('sites').filter(([, d]) => (d as SiteDoc).orgId === org);
      expect(sites.length).toBeGreaterThanOrEqual(1);
      const siteId = parseDocPath(sites[0]![0]).id;
      const screens = byCollection('screens').filter(([p]) => parseDocPath(p).parentId === siteId);
      expect(screens.length).toBeGreaterThanOrEqual(2);
      const queues = byCollection('queues').filter(([, d]) => (d as QueueDoc).siteId === siteId);
      expect(queues.length).toBeGreaterThanOrEqual(2);
      const playlists = byCollection('playlists').filter(
        ([, d]) => (d as PlaylistDoc).orgId === org,
      );
      expect(playlists.length).toBeGreaterThanOrEqual(1);
      expect((playlists[0]![1] as PlaylistDoc).items.length).toBeGreaterThan(0);
    }
  });

  it('has exactly one unclaimed device, with a pairing code', () => {
    const unclaimed = byCollection('devices').filter(([, d]) => !isClaimed(d as DeviceDoc));
    expect(unclaimed).toHaveLength(1);
    const uid = parseDocPath(unclaimed[0]![0]).id;
    const codes = byCollection('pairingCodes').map(([, d]) => (d as { uid: string }).uid);
    expect(codes).toContain(uid);
  });

  it('covers both playlist scopes (org-wide and site) and every media status that matters', () => {
    const scopes = byCollection('playlists').map(([, d]) => (d as PlaylistDoc).siteId === null);
    expect(new Set(scopes)).toEqual(new Set([true, false]));
    const statuses = byCollection('media').map(([, d]) => (d as { status: string }).status);
    expect(statuses).toEqual(expect.arrayContaining(['ready', 'processing', 'failed']));
  });

  it('uses each screen zone only where its template shows it (templates.json)', () => {
    const templates = loadTemplates();
    for (const [path, doc] of byCollection('screens')) {
      const screen = screenSchema.parse(doc);
      const template = templates.find((t) => t.id === screen.template);
      expect(template, path).toBeDefined();
      expect(zonesOutsideTemplate(template!, screen.zones), path).toEqual([]);
    }
  });

  it('covers every template', () => {
    const used = new Set(byCollection('screens').map(([, d]) => (d as ScreenDoc).template));
    expect([...used].sort()).toEqual(
      loadTemplates()
        .map((t) => t.id)
        .sort(),
    );
  });

  it('has Long colours (above Int.MAX) and both int and double numbers', () => {
    const colours = byCollection('queues').map(([, d]) => (d as QueueDoc).backgroundColor);
    expect(colours.some((c) => c > 2 ** 31 - 1)).toBe(true);
    const volumes = byCollection('screens').flatMap(([, d]) => {
      const v = (d as ScreenDoc).zones.media?.volume;
      return v === undefined ? [] : [v];
    });
    expect(volumes.some(Number.isInteger)).toBe(true);
    expect(volumes.some((v) => !Number.isInteger(v))).toBe(true);
  });
});

describe('legacy Flutter queues (queues.legacy.json)', () => {
  const legacy = loadLegacyQueueFixtures();

  it('are readable by the lenient read schema', () => {
    for (const [path, doc] of Object.entries(legacy)) {
      expect(queueReadSchema.safeParse(doc).success, path).toBe(true);
    }
  });

  it('are not valid contract queues until backfilled (no orgId/siteId)', () => {
    for (const [path, doc] of Object.entries(legacy)) {
      const r = queueSchema.safeParse(doc);
      expect(r.success, path).toBe(false);
      const keys = r.success ? [] : r.error.issues.map((i) => i.path[0]);
      expect(keys, path).toEqual(expect.arrayContaining(['orgId', 'siteId']));
    }
  });

  it('include Long colours, missing fields and a double where Flutter expects an int', () => {
    const docs = Object.values(legacy) as Record<string, unknown>[];
    expect(docs.some((d) => (d.backgroundColor as number) > 2 ** 31 - 1)).toBe(true);
    expect(docs.some((d) => !('foregroundColor' in d))).toBe(true);
    expect(docs.some((d) => !('velocity' in d) || !('icon' in d))).toBe(true);
    expect(
      docs.some((d) => typeof d.soundVolume === 'number' && !Number.isInteger(d.soundVolume)),
    ).toBe(true);
  });

  it('contract queues are readable by the read schema too', () => {
    for (const [path, doc] of byCollection('queues')) {
      expect(queueReadSchema.safeParse(doc).success, path).toBe(true);
    }
  });
});
