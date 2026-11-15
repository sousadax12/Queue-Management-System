import { z } from 'zod';
import { auditFields, docIdSchema, httpsUrlSchema, intSchema, volumeSchema } from './common.js';

export const tickerSettingsSchema = z.strictObject({
  feedUrls: z.array(httpsUrlSchema),
  speedDpPerSec: z.number().positive(),
  refreshMinutes: intSchema.positive(),
});

export const chimeSettingsSchema = z.strictObject({
  enabled: z.boolean(),
  volume: volumeSchema,
});

/** `sites/{siteId}`, written by the portal. */
export const siteSchema = z.strictObject({
  orgId: docIdSchema,
  name: z.string().min(1),
  /** IANA zone, e.g. `Europe/Lisbon`. */
  timeZone: z.string().min(1),
  /** BCP 47 tag, e.g. `pt-PT`. */
  locale: z.string().min(2),
  /** Shown by boxes whose `devices.screenId` is null (TV invariant 8). Null until the site has a screen. */
  defaultScreenId: docIdSchema.nullable(),
  ticker: tickerSettingsSchema,
  chime: chimeSettingsSchema,
  /** Local hour (0-23) for daily maintenance such as cache eviction. */
  maintenanceHour: intSchema.min(0).max(23),
  ...auditFields,
});
export type SiteDoc = z.infer<typeof siteSchema>;
