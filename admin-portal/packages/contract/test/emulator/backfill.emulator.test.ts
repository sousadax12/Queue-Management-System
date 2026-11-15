import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { queueSchema } from '../../src/index.js';
import { backfillQueues, fromFirestore, writeDocs } from '../../src/admin/index.js';
import { loadContractFixtures, loadLegacyQueueFixtures } from '../../src/fixtures.js';
import { clearFirestore, close, db, runScript } from './helpers.js';

const ORG = 'org_lojas_7Hq2';
const SITE = 'site_porto_3fKq';
const legacy = loadLegacyQueueFixtures();
const contract = loadContractFixtures();

beforeEach(async () => {
  await clearFirestore();
  await writeDocs(db, { ...contract, ...legacy });
});
afterAll(close);

const snapshot = async () =>
  Object.fromEntries((await db.collection('queues').get()).docs.map((d) => [d.id, d.data()]));

describe('queue backfill (emulator)', () => {
  it('dry run reports and writes nothing', async () => {
    const before = await snapshot();
    const report = await backfillQueues(db, { orgId: ORG, siteId: SITE });
    expect(report.applied).toBe(false);
    const actions = Object.fromEntries(report.entries.map((e) => [e.id, e.action]));
    expect(actions).toMatchObject({
      legacy_balcao_1: 'update',
      legacy_balcao_2: 'update',
      legacy_manual_3: 'update',
      q_balcao_a_P4tc: 'already-set',
      q_consultas_Ma9v: 'conflict',
    });
    expect(report.entries.find((e) => e.id === 'legacy_manual_3')?.missingLegacyFields).toEqual(
      expect.arrayContaining(['icon', 'velocity', 'videoURL', 'videoVolume']),
    );
    expect(await snapshot()).toEqual(before);
  });

  it('--apply sets orgId + siteId on legacy queues only, leaving every other field', async () => {
    const before = await snapshot();
    const report = await backfillQueues(db, {
      orgId: ORG,
      siteId: SITE,
      queueIds: ['legacy_balcao_1', 'legacy_balcao_2', 'q_consultas_Ma9v'],
      apply: true,
      by: 'uid_ops',
    });
    expect(report.updated).toBe(2);
    const after = await snapshot();
    for (const id of ['legacy_balcao_1', 'legacy_balcao_2']) {
      expect(after[id]).toMatchObject({
        ...before[id],
        orgId: ORG,
        siteId: SITE,
        updatedBy: 'uid_ops',
      });
      // a complete legacy doc becomes a valid contract queue
      expect(queueSchema.safeParse(fromFirestore(after[id])).success, id).toBe(true);
    }
    expect(after.q_consultas_Ma9v).toEqual(before.q_consultas_Ma9v);
    expect(after.legacy_manual_3).toEqual(before.legacy_manual_3);
  });

  it('refuses a site of another org, and a missing site unless allowed', async () => {
    await expect(backfillQueues(db, { orgId: 'org_clinica_K9xP', siteId: SITE })).rejects.toThrow(
      /belongs to/,
    );
    await expect(
      backfillQueues(db, { orgId: ORG, siteId: 'site_new', apply: true }),
    ).rejects.toThrow(/does not exist/);
    const r = await backfillQueues(db, {
      orgId: ORG,
      siteId: 'site_new',
      queueIds: ['legacy_balcao_1'],
      apply: true,
      allowMissingSite: true,
    });
    expect(r).toMatchObject({ siteExists: false, updated: 1 });
  });

  it('CLI is a dry run by default and exits 1 on conflicts', async () => {
    const before = await snapshot();
    const r = await runScript('backfill-queues.ts', ['--org', ORG, '--site', SITE]);
    expect(r.stdout).toContain('DRY RUN');
    expect(r.stdout).toContain('Nothing written');
    expect(r.code).toBe(1); // q_consultas_Ma9v / q_analises_Ce7y belong to another org
    expect(await snapshot()).toEqual(before);

    const applied = await runScript('backfill-queues.ts', [
      '--org',
      ORG,
      '--site',
      SITE,
      '--queue',
      'legacy_balcao_1',
      '--apply',
    ]);
    expect(applied.code, applied.stderr).toBe(0);
    expect(applied.stdout).toContain('Updated 1.');
    expect((await db.doc('queues/legacy_balcao_1').get()).get('siteId')).toBe(SITE);
  });
});
