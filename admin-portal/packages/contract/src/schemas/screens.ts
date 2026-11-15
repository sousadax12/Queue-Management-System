import { z } from 'zod';
import { auditFields, docIdSchema, intSchema, templateIdSchema, volumeSchema } from './common.js';

export const queuesZoneSchema = z.strictObject({
  /** Queues of the same site. Omitted = every queue of the site, ordered by name (TV invariant 7). */
  queueIds: z.array(docIdSchema).optional(),
  chime: z.boolean(),
});

export const mediaZoneSchema = z.strictObject({
  /** A playlist of the same site, or an org-wide one (TV invariant 10). Null = nothing to play. */
  playlistId: docIdSchema.nullable(),
  volume: volumeSchema,
});

export const tickerZoneSchema = z.strictObject({
  enabled: z.boolean(),
});

export const clockZoneSchema = z.strictObject({
  /** A date-time pattern, e.g. `HH:mm`. */
  pattern: z.string().min(1),
  showDate: z.boolean(),
});

export const zonesSchema = z.strictObject({
  queues: queuesZoneSchema.optional(),
  media: mediaZoneSchema.optional(),
  ticker: tickerZoneSchema.optional(),
  clock: clockZoneSchema.optional(),
});

/** `sites/{siteId}/screens/{screenId}`, written by the portal. A logical slot that boxes show. */
export const screenSchema = z.strictObject({
  orgId: docIdSchema,
  name: z.string().min(1),
  template: templateIdSchema,
  zones: zonesSchema,
  maxVideoHeight: intSchema.positive(),
  safeAreaPercent: intSchema.min(0).max(25),
  ...auditFields,
});
export type ScreenDoc = z.infer<typeof screenSchema>;
