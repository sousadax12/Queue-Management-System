import { describe, expect, it } from 'vitest';
import {
  PAIRING_CODE_ALPHABET,
  QUEUE_DEFAULTS,
  SERVER_TIMESTAMP,
  deviceCreateSchema,
  deviceSchema,
  mediaSchema,
  pairingCodeIdSchema,
  pairingCodeSchema,
  playlistSchema,
  queueSchema,
  randomPairingCode,
  screenSchema,
  userSchema,
} from '../src/index.js';
import { loadContractFixtures } from '../src/fixtures.js';

const fx = loadContractFixtures();
const clone = <T>(path: string): T => structuredClone(fx[path]) as T;
type Obj = Record<string, unknown>;

const PLAYLIST = 'playlists/pl_promo_nov_Zx81';
const DEVICE = 'devices/tvPortoLeft01uidAAAAAAAAAA';
const UNCLAIMED = 'devices/tvUnclaimed01uidCCCCCCCCCC';
const QUEUE = 'queues/q_balcao_a_P4tc';

describe('playlists', () => {
  it('requires mediaIds to mirror items[].mediaId (C2)', () => {
    const p = clone<Obj>(PLAYLIST);
    p.mediaIds = ['md_bunny_8f2c'];
    expect(playlistSchema.safeParse(p).success).toBe(false);
  });

  it('requires codecs on video renditions and an H.264 <= 1080p rendition (invariants 2, 3)', () => {
    const p = clone<{ items: { renditions: Obj[] }[] }>(PLAYLIST);
    delete p.items[0]!.renditions[0]!.codecs;
    expect(playlistSchema.safeParse(p).success).toBe(false);

    const hevcOnly = clone<{ items: { renditions: Obj[] }[] }>(PLAYLIST);
    hevcOnly.items[0]!.renditions[0]!.codecs = 'hvc1.1.6.L150.B0';
    expect(playlistSchema.safeParse(hevcOnly).success).toBe(false);
  });

  it('requires durationSec for images and durationMs for videos', () => {
    const p = clone<{ items: Obj[] }>(PLAYLIST);
    p.items[1]!.durationSec = null;
    expect(playlistSchema.safeParse(p).success).toBe(false);
    const v = clone<{ items: Obj[] }>(PLAYLIST);
    delete v.items[0]!.durationMs;
    expect(playlistSchema.safeParse(v).success).toBe(false);
  });

  it('rejects unknown item types and unknown fields (strict for writers)', () => {
    const p = clone<{ items: Obj[] }>(PLAYLIST);
    p.items[0]!.type = 'html';
    expect(playlistSchema.safeParse(p).success).toBe(false);
    const extra = clone<Obj>(PLAYLIST);
    extra.colour = 'red';
    expect(playlistSchema.safeParse(extra).success).toBe(false);
  });
});

describe('devices', () => {
  it('sets and clears orgId and siteId together', () => {
    const d = clone<Obj>(DEVICE);
    d.siteId = null;
    expect(deviceSchema.safeParse(d).success).toBe(false);
    d.orgId = null;
    d.screenId = null;
    expect(deviceSchema.safeParse(d).success).toBe(true); // unassigned
  });

  it('rejects a screenId without a siteId', () => {
    const d = clone<Obj>(UNCLAIMED);
    d.screenId = 'scr_lobby_left_Q2mz';
    expect(deviceSchema.safeParse(d).success).toBe(false);
  });

  it('a TV create carries only info, status and lastSeenAt', () => {
    expect(deviceCreateSchema.safeParse(clone(UNCLAIMED)).success).toBe(true);
    for (const key of ['orgId', 'siteId', 'screenId', 'name', 'identifyUntil']) {
      const d = clone<Obj>(UNCLAIMED);
      d[key] = null;
      expect(deviceCreateSchema.safeParse(d).success, key).toBe(false);
    }
  });

  it('lets a newer TV report extra info and status fields', () => {
    const d = clone<{ info: Obj; status: Obj }>(DEVICE);
    d.info.hdr = true;
    d.status.temperatureC = 51;
    expect(deviceSchema.safeParse(d).success).toBe(true);
  });

  it('requires appVersionCode (C6) and heartbeatSec (C5) as ints', () => {
    const d = clone<{ info: Obj }>(DEVICE);
    d.info.appVersionCode = 21.5;
    expect(deviceSchema.safeParse(d).success).toBe(false);
    delete d.info.appVersionCode;
    expect(deviceSchema.safeParse(d).success).toBe(false);
    const h = clone<{ info: Obj }>(DEVICE);
    delete h.info.heartbeatSec;
    expect(deviceSchema.safeParse(h).success).toBe(false);
  });
});

describe('pairing codes', () => {
  it('ids are 6 chars without 0/O/1/I', () => {
    expect(pairingCodeIdSchema.safeParse('K7M3QX').success).toBe(true);
    for (const bad of ['K7M3Q0', 'K7M3QO', 'K7M3Q1', 'K7M3QI', 'k7m3qx', 'K7M3Q', 'K7M3QXX']) {
      expect(pairingCodeIdSchema.safeParse(bad).success, bad).toBe(false);
    }
    expect(PAIRING_CODE_ALPHABET).toHaveLength(32);
    for (let i = 0; i < 200; i++)
      expect(pairingCodeIdSchema.parse(randomPairingCode())).toHaveLength(6);
  });

  it('docs hold exactly uid, createdAt and expiresAt', () => {
    const ok = {
      uid: 'u1',
      createdAt: SERVER_TIMESTAMP,
      expiresAt: { __ts: '2026-11-15T11:00:00Z' },
    };
    expect(pairingCodeSchema.safeParse(ok).success).toBe(true);
    expect(pairingCodeSchema.safeParse({ ...ok, siteId: 's' }).success).toBe(false);
    expect(pairingCodeSchema.safeParse({ ...ok, expiresAt: SERVER_TIMESTAMP }).success).toBe(false);
  });
});

describe('queues', () => {
  it('needs every legacy field so the Flutter mapper never throws (invariant 6)', () => {
    for (const key of [
      'number',
      'icon',
      'name',
      'backgroundColor',
      'videoURL',
      'videoVolume',
      'soundVolume',
      'velocity',
    ]) {
      const q = clone<Obj>(QUEUE);
      delete q[key];
      expect(queueSchema.safeParse(q).success, key).toBe(false);
    }
  });

  it('keeps Flutter int fields int and number within 0..99', () => {
    for (const [key, value] of [
      ['number', 100],
      ['number', 4.5],
      ['videoVolume', 0.5],
      ['velocity', 60.5],
      ['backgroundColor', 2 ** 32],
    ] as const) {
      const q = clone<Obj>(QUEUE);
      q[key] = value;
      expect(queueSchema.safeParse(q).success, `${key}=${value}`).toBe(false);
    }
  });

  it('defaults make a valid new queue', () => {
    const q = { orgId: 'org_x', siteId: 'site_x', name: 'Nova', ...QUEUE_DEFAULTS };
    expect(queueSchema.safeParse(q).success).toBe(true);
  });
});

describe('screens', () => {
  it('rejects templates outside the app enum', () => {
    const s = clone<Obj>('sites/site_porto_3fKq/screens/scr_lobby_left_Q2mz');
    s.template = 'weather';
    expect(screenSchema.safeParse(s).success).toBe(false);
  });
});

describe('media (C1)', () => {
  it('ready media needs renditions; failed media needs an error', () => {
    const m = clone<Obj>('media/md_bunny_8f2c');
    m.renditions = [];
    expect(mediaSchema.safeParse(m).success).toBe(false);
    const f = clone<Obj>('media/md_hevc_4b7a');
    f.error = null;
    expect(mediaSchema.safeParse(f).success).toBe(false);
  });

  it('only allows the C1 statuses', () => {
    const m = clone<Obj>('media/md_upload_9d1e');
    m.status = 'uploaded';
    expect(mediaSchema.safeParse(m).success).toBe(false);
  });
});

describe('users', () => {
  it('ties roles to orgs and sites', () => {
    const base = { email: 'a@b.example', displayName: null, sites: [] as string[] };
    expect(userSchema.safeParse({ ...base, role: 'platformAdmin', orgId: null }).success).toBe(
      true,
    );
    expect(userSchema.safeParse({ ...base, role: 'platformAdmin', orgId: 'org_x' }).success).toBe(
      false,
    );
    expect(userSchema.safeParse({ ...base, role: 'orgAdmin', orgId: null }).success).toBe(false);
    expect(userSchema.safeParse({ ...base, role: 'operator', orgId: 'org_x' }).success).toBe(false);
    expect(
      userSchema.safeParse({ ...base, role: 'operator', orgId: 'org_x', sites: ['s'] }).success,
    ).toBe(true);
  });
});
