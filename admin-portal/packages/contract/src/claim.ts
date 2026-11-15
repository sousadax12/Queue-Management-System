import { SERVER_TIMESTAMP, isEncodedTimestamp, timestampToMillis } from './encoding.js';
import {
  type DeviceClaimPatch,
  type DeviceDoc,
  deviceClaimPatchSchema,
  deviceSchema,
} from './schemas/devices.js';
import {
  PAIRING_CODE_MAX_AGE_MS,
  type PairingCodeDoc,
  pairingCodeIdSchema,
  pairingCodeSchema,
} from './schemas/pairing-codes.js';
import { type ScreenDoc, screenSchema } from './schemas/screens.js';
import { type SiteDoc, siteSchema } from './schemas/sites.js';

/**
 * The claim transaction (TV design "Auth and rules", step 3; portal A5), written once
 * against a tiny port so the seed script (Admin SDK) and the portal (web SDK) run the
 * same logic. All reads happen before any write, as Firestore transactions require.
 * Docs cross the port in the contract's encoded form (`{__ts}`, `{__sentinel}`).
 */
export interface ClaimTransaction {
  getPairingCode(code: string): Promise<unknown>;
  getSite(siteId: string): Promise<unknown>;
  getScreen(siteId: string, screenId: string): Promise<unknown>;
  getDevice(uid: string): Promise<unknown>;
  updateDevice(uid: string, patch: DeviceClaimPatch): void;
  deletePairingCode(code: string): void;
}

export type RunClaimTransaction = <T>(fn: (tx: ClaimTransaction) => Promise<T>) => Promise<T>;

export interface ClaimInput {
  code: string;
  siteId: string;
  screenId: string;
  /** Box label in the fleet table. Defaults to the device's current name, else `TV <code>`. */
  name?: string | undefined;
  /** uid of the staff user claiming (written as `updatedBy`). */
  by: string;
  /** Clock used for the 15-minute check; defaults to `Date.now()`. */
  now?: number;
}

export interface ClaimResult {
  uid: string;
  orgId: string;
  siteId: string;
  screenId: string;
  name: string;
  /** True when the box was already claimed (in the same org) and has been moved. */
  moved: boolean;
}

export type ClaimErrorCode =
  | 'invalid_code'
  | 'code_not_found'
  | 'code_expired'
  | 'site_not_found'
  | 'screen_not_found'
  | 'device_not_found'
  | 'device_in_other_org'
  | 'invalid_document';

export class ClaimError extends Error {
  constructor(
    readonly code: ClaimErrorCode,
    message: string,
  ) {
    super(message);
    this.name = 'ClaimError';
  }
}

function parseOrThrow<T>(
  schema: { safeParse(v: unknown): { success: true; data: T } | { success: false; error: Error } },
  value: unknown,
  what: string,
): T {
  const r = schema.safeParse(value);
  if (!r.success)
    throw new ClaimError('invalid_document', `${what} breaks the contract: ${r.error.message}`);
  return r.data;
}

/** Pure check of a pairing code's age by its server `createdAt` (C4). */
export function isPairingCodeExpired(code: PairingCodeDoc, now: number): boolean {
  if (!isEncodedTimestamp(code.createdAt)) return false; // unresolved sentinel: just written
  return now - timestampToMillis(code.createdAt) > PAIRING_CODE_MAX_AGE_MS;
}

export async function claimPairingCode(
  run: RunClaimTransaction,
  input: ClaimInput,
): Promise<ClaimResult> {
  const code = input.code.trim().toUpperCase();
  if (!pairingCodeIdSchema.safeParse(code).success) {
    throw new ClaimError('invalid_code', `"${input.code}" is not a pairing code`);
  }
  const now = input.now ?? Date.now();

  return run(async (tx) => {
    const rawCode = await tx.getPairingCode(code);
    if (rawCode == null) throw new ClaimError('code_not_found', `No pairing code ${code}`);
    const pairing = parseOrThrow<PairingCodeDoc>(
      pairingCodeSchema,
      rawCode,
      `pairingCodes/${code}`,
    );
    if (isPairingCodeExpired(pairing, now)) {
      throw new ClaimError('code_expired', `Pairing code ${code} is older than 15 minutes`);
    }

    const rawSite = await tx.getSite(input.siteId);
    if (rawSite == null) throw new ClaimError('site_not_found', `No site ${input.siteId}`);
    const site = parseOrThrow<SiteDoc>(siteSchema, rawSite, `sites/${input.siteId}`);

    const rawScreen = await tx.getScreen(input.siteId, input.screenId);
    if (rawScreen == null) {
      throw new ClaimError(
        'screen_not_found',
        `No screen ${input.screenId} in site ${input.siteId}`,
      );
    }
    parseOrThrow<ScreenDoc>(
      screenSchema,
      rawScreen,
      `sites/${input.siteId}/screens/${input.screenId}`,
    );

    const rawDevice = await tx.getDevice(pairing.uid);
    if (rawDevice == null) throw new ClaimError('device_not_found', `No device ${pairing.uid}`);
    const device = parseOrThrow<DeviceDoc>(deviceSchema, rawDevice, `devices/${pairing.uid}`);

    const currentOrg = device.orgId ?? null;
    if (currentOrg !== null && currentOrg !== site.orgId) {
      throw new ClaimError(
        'device_in_other_org',
        `Device ${pairing.uid} belongs to ${currentOrg}; only a platform admin can move it to ${site.orgId}`,
      );
    }

    const name = input.name?.trim() || device.name || `TV ${code}`;
    const patch = deviceClaimPatchSchema.parse({
      orgId: site.orgId,
      siteId: input.siteId,
      screenId: input.screenId,
      name,
      updatedAt: SERVER_TIMESTAMP,
      updatedBy: input.by,
    });
    tx.updateDevice(pairing.uid, patch);
    tx.deletePairingCode(code);

    return {
      uid: pairing.uid,
      orgId: site.orgId,
      siteId: input.siteId,
      screenId: input.screenId,
      name,
      moved: currentOrg !== null,
    };
  });
}
