import { describe, expect, it } from 'vitest';
import {
  ClaimError,
  type ClaimInput,
  type ClaimTransaction,
  type DeviceClaimPatch,
  type RunClaimTransaction,
  claimPairingCode,
  paths,
  resolveSentinels,
} from '../src/index.js';
import { loadContractFixtures } from '../src/fixtures.js';

const NOW = Date.parse('2026-11-15T10:00:00Z');
const CODE = 'K7M3QX';
const UID = 'tvUnclaimed01uidCCCCCCCCCC';
const SITE = 'site_porto_3fKq';
const SCREEN = 'scr_lobby_left_Q2mz';

/** An in-memory store whose transaction records reads and writes in order. */
function fakeStore(docs: Record<string, unknown>) {
  const log: string[] = [];
  const run: RunClaimTransaction = async (fn) => {
    const tx: ClaimTransaction = {
      getPairingCode: (code) => read(paths.pairingCode(code)),
      getSite: (id) => read(paths.site(id)),
      getScreen: (siteId, id) => read(paths.screen(siteId, id)),
      getDevice: (uid) => read(paths.device(uid)),
      updateDevice: (uid, patch: DeviceClaimPatch) => {
        log.push(`update ${paths.device(uid)}`);
        docs[paths.device(uid)] = { ...(docs[paths.device(uid)] as object), ...patch };
      },
      deletePairingCode: (code) => {
        log.push(`delete ${paths.pairingCode(code)}`);
        delete docs[paths.pairingCode(code)];
      },
    };
    function read(path: string) {
      log.push(`get ${path}`);
      return Promise.resolve(docs[path] ?? null);
    }
    return fn(tx);
  };
  return { run, log, docs };
}

function seeded(createdAt = '2026-11-15T09:50:00Z') {
  const docs = resolveSentinels(loadContractFixtures(), { __ts: createdAt });
  return fakeStore(docs);
}

const input: ClaimInput = { code: CODE, siteId: SITE, screenId: SCREEN, by: 'uid_admin', now: NOW };

describe('claimPairingCode', () => {
  it('sets orgId/siteId/screenId/name on the device and deletes the code, reads first', async () => {
    const s = seeded();
    const r = await claimPairingCode(s.run, { ...input, name: 'Box 9' });
    expect(r).toEqual({
      uid: UID,
      orgId: 'org_lojas_7Hq2',
      siteId: SITE,
      screenId: SCREEN,
      name: 'Box 9',
      moved: false,
    });
    expect(s.docs[paths.device(UID)]).toMatchObject({
      orgId: 'org_lojas_7Hq2',
      siteId: SITE,
      screenId: SCREEN,
      name: 'Box 9',
      updatedAt: { __sentinel: 'serverTimestamp' },
      updatedBy: 'uid_admin',
    });
    expect(s.docs[paths.pairingCode(CODE)]).toBeUndefined();
    const firstWrite = s.log.findIndex((l) => !l.startsWith('get'));
    expect(s.log.slice(firstWrite).every((l) => !l.startsWith('get'))).toBe(true);
  });

  it('defaults the name to "TV <code>" and normalises the code', async () => {
    const r = await claimPairingCode(seeded().run, { ...input, code: ' k7m3qx ' });
    expect(r.name).toBe('TV K7M3QX');
  });

  it('accepts a code exactly 15 minutes old and rejects one a millisecond older (C4)', async () => {
    await expect(
      claimPairingCode(seeded('2026-11-15T09:45:00Z').run, input),
    ).resolves.toBeDefined();
    const s = seeded('2026-11-15T09:44:59.999Z');
    await expect(claimPairingCode(s.run, input)).rejects.toMatchObject({ code: 'code_expired' });
    expect(s.log.some((l) => !l.startsWith('get'))).toBe(false);
  });

  it.each([
    [{ code: 'K7M3Q0' }, 'invalid_code'],
    [{ code: 'ZZZZZZ' }, 'code_not_found'],
    [{ siteId: 'site_nope' }, 'site_not_found'],
    [{ screenId: 'scr_nope' }, 'screen_not_found'],
    [{ screenId: 'scr_waiting_Hn5r' }, 'screen_not_found'], // a screen of another site
  ] as const)('rejects %j with %s', async (patch, code) => {
    const s = seeded();
    await expect(claimPairingCode(s.run, { ...input, ...patch })).rejects.toMatchObject({ code });
    expect(s.docs[paths.pairingCode(CODE)]).toBeDefined();
  });

  it('rejects a code whose device doc is missing', async () => {
    const s = seeded();
    delete s.docs[paths.device(UID)];
    await expect(claimPairingCode(s.run, input)).rejects.toMatchObject({
      code: 'device_not_found',
    });
  });

  it('refuses to move a box claimed in another org; moves one within the org', async () => {
    const s = seeded();
    s.docs[paths.pairingCode(CODE)] = {
      ...(s.docs[paths.pairingCode(CODE)] as object),
      uid: 'tvLisboa01uidBBBBBBBBBBBBB',
    };
    await expect(claimPairingCode(s.run, input)).rejects.toBeInstanceOf(ClaimError);
    await expect(claimPairingCode(s.run, input)).rejects.toMatchObject({
      code: 'device_in_other_org',
    });

    const t = seeded();
    t.docs[paths.pairingCode(CODE)] = {
      ...(t.docs[paths.pairingCode(CODE)] as object),
      uid: 'tvPortoLeft01uidAAAAAAAAAA',
    };
    const r = await claimPairingCode(t.run, { ...input, screenId: 'scr_lobby_right_Vb8n' });
    expect(r).toMatchObject({ moved: true, name: 'Box 3 (left of door)' });
  });

  it('refuses documents that break the contract', async () => {
    const s = seeded();
    s.docs[paths.pairingCode(CODE)] = { uid: UID, createdAt: 'yesterday' };
    await expect(claimPairingCode(s.run, input)).rejects.toMatchObject({
      code: 'invalid_document',
    });
  });
});
