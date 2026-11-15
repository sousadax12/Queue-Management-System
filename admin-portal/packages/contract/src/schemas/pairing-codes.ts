import { z } from 'zod';
import { timestampSchema, uidSchema, writeTimestampSchema } from './common.js';

/** 6 characters from an unambiguous alphabet: no 0/O/1/I. */
export const PAIRING_CODE_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
export const PAIRING_CODE_LENGTH = 6;
/** The portal treats a code as expired at server `createdAt` + 15 min (C4). */
export const PAIRING_CODE_MAX_AGE_MS = 15 * 60 * 1000;

export const pairingCodeIdSchema = z
  .string()
  .regex(
    new RegExp(`^[${PAIRING_CODE_ALPHABET}]{${PAIRING_CODE_LENGTH}}$`),
    `must be ${PAIRING_CODE_LENGTH} characters from ${PAIRING_CODE_ALPHABET}`,
  );

/**
 * `pairingCodes/{code}`: created by the TV, read and deleted by the portal on claim.
 * Exactly these three keys. `createdAt` is `serverTimestamp()` on write; `expiresAt`
 * (server-time estimate + 1 h) exists only for the TTL policy.
 */
export const pairingCodeSchema = z.strictObject({
  uid: uidSchema,
  createdAt: writeTimestampSchema,
  expiresAt: timestampSchema,
});
export type PairingCodeDoc = z.infer<typeof pairingCodeSchema>;

/** A random code; uniform over the alphabet (32 symbols, so no modulo bias). */
export function randomPairingCode(): string {
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(PAIRING_CODE_LENGTH));
  return Array.from(bytes, (b) => PAIRING_CODE_ALPHABET[b % PAIRING_CODE_ALPHABET.length]).join('');
}
