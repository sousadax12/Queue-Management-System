import { z } from 'zod';
import {
  docIdSchema,
  intSchema,
  nonNegativeIntSchema,
  optionalAuditFields,
  timestampSchema,
  uidSchema,
  writeTimestampSchema,
} from './common.js';

/** Written by the TV. Loose: a newer app may report more. */
export const deviceInfoSchema = z.looseObject({
  model: z.string(),
  sdkInt: intSchema.positive(),
  /** Display string, e.g. `0.6.0 (21)`. Do not parse it; use appVersionCode. */
  appVersion: z.string(),
  /** The app's versionCode; filters templates against templates.json (C6). */
  appVersionCode: intSchema.positive(),
  /** Heartbeat interval in seconds, default 600 (C5). Offline after 2.5 x this. */
  heartbeatSec: intSchema.positive(),
  decoders: z.array(z.string()),
  /** `WIDTHxHEIGHT`. */
  maxDisplay: z.string().regex(/^\d+x\d+$/),
});

/** Written by the TV once per heartbeat, on sync-complete and on a new error (C5). Loose. */
export const deviceStatusSchema = z.looseObject({
  nowPlaying: docIdSchema.nullable(),
  cacheBytes: nonNegativeIntSchema,
  freeBytes: nonNegativeIntSchema,
  lastSyncAt: timestampSchema.nullable(),
  pendingItems: nonNegativeIntSchema,
  lastError: z.string().nullable(),
});

/** Keys only the portal writes. The rules reject a TV create that has any of them. */
export const PORTAL_ONLY_DEVICE_KEYS = [
  'orgId',
  'siteId',
  'screenId',
  'name',
  'identifyUntil',
  'updatedAt',
  'updatedBy',
] as const;

/** Keys the TV may write on its own doc. */
export const TV_DEVICE_KEYS = ['info', 'status', 'lastSeenAt'] as const;

const tvFields = {
  info: deviceInfoSchema,
  status: deviceStatusSchema,
  lastSeenAt: writeTimestampSchema,
};

/** The TV's create of `devices/{uid}`: only `info`, `status` and `lastSeenAt`. */
export const deviceCreateSchema = z.strictObject(tvFields);
export type DeviceCreateDoc = z.infer<typeof deviceCreateSchema>;

/**
 * `devices/{uid}`. Unclaimed: no org/site/screen. Claimed: `orgId` + `siteId` set together;
 * `screenId: null` shows the site's default screen (TV invariant 8).
 */
export const deviceSchema = z
  .strictObject({
    ...tvFields,
    orgId: docIdSchema.nullable().optional(),
    siteId: docIdSchema.nullable().optional(),
    screenId: docIdSchema.nullable().optional(),
    name: z.string().min(1).nullable().optional(),
    /** Portal sets now + 60 s to flash the box's identity overlay. */
    identifyUntil: timestampSchema.nullable().optional(),
    ...optionalAuditFields,
  })
  .superRefine((d, ctx) => {
    const org = d.orgId ?? null;
    const site = d.siteId ?? null;
    const screen = d.screenId ?? null;
    if ((org === null) !== (site === null)) {
      ctx.addIssue({
        code: 'custom',
        path: ['siteId'],
        message: 'orgId and siteId are set and cleared together',
      });
    }
    if (screen !== null && site === null) {
      ctx.addIssue({ code: 'custom', path: ['screenId'], message: 'screenId needs a siteId' });
    }
  });
export type DeviceDoc = z.infer<typeof deviceSchema>;

export function isClaimed(d: Pick<DeviceDoc, 'orgId' | 'siteId'>): boolean {
  return (d.orgId ?? null) !== null && (d.siteId ?? null) !== null;
}

/** The fields a claim writes (A5 and the seed script's `claim`). */
export const deviceClaimPatchSchema = z.strictObject({
  orgId: docIdSchema,
  siteId: docIdSchema,
  screenId: docIdSchema,
  name: z.string().min(1),
  updatedAt: writeTimestampSchema,
  updatedBy: uidSchema,
});
export type DeviceClaimPatch = z.infer<typeof deviceClaimPatchSchema>;
