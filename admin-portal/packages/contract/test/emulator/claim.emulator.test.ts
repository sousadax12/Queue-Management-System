import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { ClaimError, PAIRING_CODE_MAX_AGE_MS } from '../../src/index.js';
import {
  claimWithAdmin,
  createPairingCode,
  writeDocs,
  writePairingCodeAt,
} from '../../src/admin/index.js';
import { loadContractFixtures } from '../../src/fixtures.js';
import { clearFirestore, close, db, runScript } from './helpers.js';

const UID = 'tvUnclaimed01uidCCCCCCCCCC';
const SITE = 'site_porto_3fKq';
const SCREEN = 'scr_lobby_left_Q2mz';

beforeEach(async () => {
  await clearFirestore();
  await writeDocs(db, loadContractFixtures());
});
afterAll(close);

describe('claim (emulator)', () => {
  it('claims the seeded code in one transaction', async () => {
    const r = await claimWithAdmin(db, {
      code: 'K7M3QX',
      siteId: SITE,
      screenId: SCREEN,
      name: 'Box 9',
      by: 'uid_x',
    });
    expect(r).toMatchObject({ uid: UID, orgId: 'org_lojas_7Hq2', moved: false });
    const device = (await db.doc(`devices/${UID}`).get()).data();
    expect(device).toMatchObject({
      orgId: 'org_lojas_7Hq2',
      siteId: SITE,
      screenId: SCREEN,
      name: 'Box 9',
      updatedBy: 'uid_x',
    });
    expect(device?.info).toBeDefined(); // TV fields untouched
    expect((await db.doc('pairingCodes/K7M3QX').get()).exists).toBe(false);

    await expect(
      claimWithAdmin(db, { code: 'K7M3QX', siteId: SITE, screenId: SCREEN, by: 'uid_x' }),
    ).rejects.toMatchObject({ code: 'code_not_found' });
  });

  it('judges expiry by the server createdAt (C4)', async () => {
    await writePairingCodeAt(
      db,
      UID,
      'EXP1RD'.replace('1', '7'),
      new Date(Date.now() - PAIRING_CODE_MAX_AGE_MS - 60_000),
    );
    await expect(
      claimWithAdmin(db, { code: 'EXP7RD', siteId: SITE, screenId: SCREEN, by: 'uid_x' }),
    ).rejects.toMatchObject({ code: 'code_expired' });
    expect((await db.doc('pairingCodes/EXP7RD').get()).exists).toBe(true);
    expect((await db.doc(`devices/${UID}`).get()).get('orgId')).toBeUndefined();
  });

  it('creates codes like the TV: a second create of the same code fails (collision)', async () => {
    const code = await createPairingCode(db, UID);
    await expect(createPairingCode(db, UID, code)).rejects.toThrow();
    const r = await claimWithAdmin(db, { code, siteId: SITE, screenId: SCREEN, by: 'uid_x' });
    expect(r.name).toBe(`TV ${code}`);
  });

  it('keeps a box in its org', async () => {
    await createPairingCode(db, 'tvLisboa01uidBBBBBBBBBBBBB', 'MVB0XX'.replace('0', '8'));
    const err: unknown = await claimWithAdmin(db, {
      code: 'MVB8XX',
      siteId: SITE,
      screenId: SCREEN,
      by: 'uid_x',
    }).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ClaimError);
    expect((err as ClaimError).code).toBe('device_in_other_org');
  });

  it('works from the CLI: seed claim <code> <siteId> <screenId>', async () => {
    const r = await runScript('seed.ts', ['claim', 'K7M3QX', SITE, SCREEN, '--name', 'Entrada']);
    expect(r.code, r.stderr).toBe(0);
    expect(r.stdout).toContain(`Claimed ${UID} as "Entrada"`);
    expect((await db.doc(`devices/${UID}`).get()).get('screenId')).toBe(SCREEN);

    const again = await runScript('seed.ts', ['claim', 'K7M3QX', SITE, SCREEN]);
    expect(again.code).toBe(1);
    expect(again.stderr).toContain('code_not_found');
  });

  it('pairing-code then claim from the CLI', async () => {
    const created = await runScript('seed.ts', ['pairing-code', '--code', 'HJKL23']);
    expect(created.code, created.stderr).toBe(0);
    const r = await runScript('seed.ts', [
      'claim',
      'HJKL23',
      'site_lisboa_Wd2e',
      'scr_waiting_Hn5r',
    ]);
    expect(r.code, r.stderr).toBe(0);
    expect((await db.doc(`devices/${UID}`).get()).get('orgId')).toBe('org_clinica_K9xP');
  });
});
