import { type Firestore, Timestamp } from 'firebase-admin/firestore';
import { type CollectionName, parseDocPath, paths } from '../collections.js';
import { SERVER_TIMESTAMP } from '../encoding.js';
import { pairingCodeSchema, randomPairingCode } from '../schemas/pairing-codes.js';
import { toFirestore } from './codec.js';

export interface SeedResult {
  written: number;
  byCollection: Partial<Record<CollectionName, number>>;
}

const BATCH_LIMIT = 450;

/**
 * Write contract docs (keyed by path, in the fixture encoding) with `set()`, so seeding
 * is idempotent. Sentinels become `serverTimestamp()`.
 */
export async function writeDocs(
  db: Firestore,
  docs: Readonly<Record<string, unknown>>,
  opts: { skip?: readonly CollectionName[] } = {},
): Promise<SeedResult> {
  const result: SeedResult = { written: 0, byCollection: {} };
  let batch = db.batch();
  let inBatch = 0;
  for (const [path, doc] of Object.entries(docs)) {
    const { collection } = parseDocPath(path);
    if (opts.skip?.includes(collection)) continue;
    batch.set(db.doc(path), toFirestore(doc) as Record<string, unknown>);
    result.written++;
    result.byCollection[collection] = (result.byCollection[collection] ?? 0) + 1;
    if (++inBatch === BATCH_LIMIT) {
      await batch.commit();
      batch = db.batch();
      inBatch = 0;
    }
  }
  if (inBatch > 0) await batch.commit();
  return result;
}

/**
 * Create a pairing code the way the TV does: `createdAt: serverTimestamp()`, `expiresAt`
 * = now + 1 h, and `create()` so an existing code fails like the rules' collision.
 */
export async function createPairingCode(
  db: Firestore,
  uid: string,
  code: string = randomPairingCode(),
): Promise<string> {
  const doc = pairingCodeSchema.parse({
    uid,
    createdAt: SERVER_TIMESTAMP,
    expiresAt: { __ts: new Date(Date.now() + 60 * 60 * 1000).toISOString() },
  });
  await db.doc(paths.pairingCode(code)).create(toFirestore(doc) as Record<string, unknown>);
  return code;
}

/** For tests: a code whose server `createdAt` is in the past. */
export async function writePairingCodeAt(
  db: Firestore,
  uid: string,
  code: string,
  createdAt: Date,
): Promise<void> {
  await db.doc(paths.pairingCode(code)).set({
    uid,
    createdAt: Timestamp.fromDate(createdAt),
    expiresAt: Timestamp.fromMillis(createdAt.getTime() + 60 * 60 * 1000),
  });
}
