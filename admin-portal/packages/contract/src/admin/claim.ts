import type { DocumentSnapshot, Firestore } from 'firebase-admin/firestore';
import {
  type ClaimInput,
  type ClaimResult,
  type RunClaimTransaction,
  claimPairingCode,
} from '../claim.js';
import { paths } from '../collections.js';
import { fromFirestore, toFirestore } from './codec.js';

const data = (s: DocumentSnapshot): unknown => (s.exists ? fromFirestore(s.data()) : null);

/** The claim transaction on the Admin SDK. The portal (A5) wires the same logic to the web SDK. */
export function adminClaimRunner(db: Firestore): RunClaimTransaction {
  return (fn) =>
    db.runTransaction(async (t) =>
      fn({
        getPairingCode: async (code) => data(await t.get(db.doc(paths.pairingCode(code)))),
        getSite: async (siteId) => data(await t.get(db.doc(paths.site(siteId)))),
        getScreen: async (siteId, screenId) =>
          data(await t.get(db.doc(paths.screen(siteId, screenId)))),
        getDevice: async (uid) => data(await t.get(db.doc(paths.device(uid)))),
        updateDevice: (uid, patch) => {
          t.update(db.doc(paths.device(uid)), toFirestore(patch) as Record<string, unknown>);
        },
        deletePairingCode: (code) => {
          t.delete(db.doc(paths.pairingCode(code)));
        },
      }),
    );
}

export function claimWithAdmin(db: Firestore, input: ClaimInput): Promise<ClaimResult> {
  return claimPairingCode(adminClaimRunner(db), input);
}
