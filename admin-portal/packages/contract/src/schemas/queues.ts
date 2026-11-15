import { z } from 'zod';
import { docIdSchema, intSchema, optionalAuditFields } from './common.js';

/** ARGB colour as Flutter's `Color.value`: an unsigned 32-bit int, so a Long on Android (> Int.MAX). */
export const argbSchema = intSchema.min(0).max(0xffffffff);
/** A Font Awesome Solid code point, as `font_awesome_flutter` stores it. */
export const iconCodePointSchema = intSchema.min(0).max(0x10ffff);

/**
 * The fields the Flutter mapper reads with `document['x']` (lib/mappers/queue_doc_mapper.dart).
 * It throws if one is missing, and assigns them to `int`/`String` fields, so a double breaks it.
 */
const legacyFields = {
  number: intSchema.min(0).max(99),
  icon: iconCodePointSchema,
  name: z.string().min(1),
  backgroundColor: argbSchema,
  /** Free-form URL streamed by the legacy Flutter display; empty = none. */
  videoURL: z.union([z.literal(''), z.url()]),
  /** 0 or 1: Flutter passes it to video_player as a 0..1 gain. */
  videoVolume: intSchema.min(0).max(1),
  soundVolume: intSchema.min(0).max(1),
  /** RSS ticker speed in logical pixels per second (legacy). */
  velocity: intSchema.nonnegative(),
};

/**
 * `queues/{queueId}` as the portal writes it: every legacy field (TV invariant 6) plus
 * `orgId` and `siteId` (C10). `number` is also written by the Flutter operator, whose
 * `update({'number': n})` leaves the audit fields alone, so they are optional.
 */
export const queueSchema = z.strictObject({
  orgId: docIdSchema,
  siteId: docIdSchema,
  ...legacyFields,
  /** Defaults to white (0xFFFFFFFF) when absent. */
  foregroundColor: argbSchema.optional(),
  ...optionalAuditFields,
});
export type QueueDoc = z.infer<typeof queueSchema>;

/**
 * What a reader may find in `queues` today: Flutter-era docs without `orgId`/`siteId`
 * (until the backfill runs), without `foregroundColor`, and hand-made docs that miss
 * legacy fields. Readers default anything missing and accept Long or Double numbers.
 */
export const queueReadSchema = z.looseObject({
  orgId: docIdSchema.optional(),
  siteId: docIdSchema.optional(),
  number: z.number().optional(),
  icon: z.number().optional(),
  name: z.string().optional(),
  backgroundColor: z.number().optional(),
  foregroundColor: z.number().optional(),
  videoURL: z.string().optional(),
  videoVolume: z.number().optional(),
  soundVolume: z.number().optional(),
  velocity: z.number().optional(),
});
export type QueueReadDoc = z.infer<typeof queueReadSchema>;

export const LEGACY_QUEUE_FIELDS = Object.keys(legacyFields) as (keyof typeof legacyFields)[];

/** Neutral defaults for a new queue, so the Flutter mapper never throws (TV invariant 6). */
export const QUEUE_DEFAULTS = {
  number: 0,
  icon: 0xf007, // fa-user
  backgroundColor: 0xff2d5b9f,
  foregroundColor: 0xffffffff,
  videoURL: '',
  videoVolume: 0,
  soundVolume: 1,
  velocity: 60,
} as const satisfies Partial<QueueDoc>;
