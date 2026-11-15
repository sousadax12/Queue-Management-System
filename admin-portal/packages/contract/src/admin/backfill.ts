import type { Firestore } from 'firebase-admin/firestore';
import { paths } from '../collections.js';
import { SERVER_TIMESTAMP } from '../encoding.js';
import { LEGACY_QUEUE_FIELDS, queueReadSchema } from '../schemas/queues.js';
import { siteSchema } from '../schemas/sites.js';
import { fromFirestore, toFirestore } from './codec.js';

export interface QueueBackfillOptions {
  orgId: string;
  siteId: string;
  /** Limit to these queue ids. Default: every queue. */
  queueIds?: readonly string[] | undefined;
  /** Write. Default false: dry run. */
  apply?: boolean;
  /** Proceed when `sites/{siteId}` does not exist yet (it must still be created before TVs use it). */
  allowMissingSite?: boolean;
  /** Written as `updatedBy`. */
  by?: string;
}

export type QueueBackfillAction = 'update' | 'already-set' | 'conflict';

export interface QueueBackfillEntry {
  id: string;
  name: string | undefined;
  action: QueueBackfillAction;
  /** Current values, for the report. */
  orgId: string | undefined;
  siteId: string | undefined;
  /** Legacy fields the Flutter mapper needs but the doc lacks (TV invariant 6). */
  missingLegacyFields: string[];
}

export interface QueueBackfillReport {
  applied: boolean;
  siteExists: boolean;
  entries: QueueBackfillEntry[];
  updated: number;
}

/**
 * One-off: set `orgId` + `siteId` on existing `queues` docs (C10, TV invariant 6).
 * Dry run unless `apply`. Never overwrites a different existing value (reported as conflict),
 * and touches only `orgId`, `siteId`, `updatedAt` and `updatedBy`.
 */
export async function backfillQueues(
  db: Firestore,
  opts: QueueBackfillOptions,
): Promise<QueueBackfillReport> {
  const siteSnap = await db.doc(paths.site(opts.siteId)).get();
  const siteExists = siteSnap.exists;
  if (siteExists) {
    const site = siteSchema.parse(fromFirestore(siteSnap.data()));
    if (site.orgId !== opts.orgId) {
      throw new Error(`Site ${opts.siteId} belongs to ${site.orgId}, not ${opts.orgId}`);
    }
  } else if (opts.apply && !opts.allowMissingSite) {
    throw new Error(
      `Site ${opts.siteId} does not exist. Create it first, or pass --allow-missing-site.`,
    );
  }

  const snaps = opts.queueIds?.length
    ? await db.getAll(...opts.queueIds.map((id) => db.doc(paths.queue(id))))
    : (await db.collection('queues').get()).docs;

  const entries: QueueBackfillEntry[] = [];
  for (const s of snaps) {
    if (!s.exists) throw new Error(`Queue ${s.id} does not exist`);
    const q = queueReadSchema.parse(fromFirestore(s.data()));
    const conflict =
      (q.orgId !== undefined && q.orgId !== opts.orgId) ||
      (q.siteId !== undefined && q.siteId !== opts.siteId);
    const done = q.orgId === opts.orgId && q.siteId === opts.siteId;
    entries.push({
      id: s.id,
      name: q.name,
      action: conflict ? 'conflict' : done ? 'already-set' : 'update',
      orgId: q.orgId,
      siteId: q.siteId,
      missingLegacyFields: LEGACY_QUEUE_FIELDS.filter((f) => q[f] === undefined),
    });
  }

  const toUpdate = entries.filter((e) => e.action === 'update');
  if (opts.apply) {
    const patch = toFirestore({
      orgId: opts.orgId,
      siteId: opts.siteId,
      updatedAt: SERVER_TIMESTAMP,
      updatedBy: opts.by ?? 'script:backfill-queues',
    }) as Record<string, unknown>;
    for (let i = 0; i < toUpdate.length; i += 450) {
      const batch = db.batch();
      for (const e of toUpdate.slice(i, i + 450)) batch.update(db.doc(paths.queue(e.id)), patch);
      await batch.commit();
    }
  }
  return {
    applied: Boolean(opts.apply),
    siteExists,
    entries,
    updated: opts.apply ? toUpdate.length : 0,
  };
}
