import { z } from 'zod';
import { timestampSchema, writeTimestampSchema } from '../encoding.js';

/** A Firestore document id: no slash, not `.`/`..`, not `__x__`. Ids are globally unique (C10). */
export const docIdSchema = z
  .string()
  .min(1)
  .max(1500)
  .refine((s) => !s.includes('/'), 'must not contain "/"')
  .refine((s) => s !== '.' && s !== '..', 'must not be "." or ".."')
  .refine((s) => !/^__.*__$/.test(s), 'must not match __.*__');

/** A Firebase Auth uid (portal staff or a TV's anonymous user). */
export const uidSchema = z.string().min(1).max(128);

export const httpsUrlSchema = z.url({ protocol: /^https$/ });

export const intSchema = z.number().int();
export const nonNegativeIntSchema = z.number().int().nonnegative();
/** A 0..1 gain. Integers 0 and 1 are valid: Firestore may store `1.0` as an integer (TV invariant 11). */
export const volumeSchema = z.number().min(0).max(1);

/** Audit fields on every portal write to sites, screens, queues, playlists and devices (C9). */
export const auditFields = {
  updatedAt: writeTimestampSchema,
  updatedBy: uidSchema,
};

/** Audit fields on docs that also have writers other than the portal (legacy queues, TV-created devices). */
export const optionalAuditFields = {
  updatedAt: writeTimestampSchema.optional(),
  updatedBy: uidSchema.optional(),
};

export { timestampSchema, writeTimestampSchema };

export const TEMPLATE_IDS = [
  'queues_media',
  'fullscreen_media',
  'media_ticker_clock',
  'queues_only',
] as const;
export const templateIdSchema = z.enum(TEMPLATE_IDS);
export type TemplateId = z.infer<typeof templateIdSchema>;

export const ZONE_IDS = ['queues', 'media', 'ticker', 'clock'] as const;
export const zoneIdSchema = z.enum(ZONE_IDS);
export type ZoneId = z.infer<typeof zoneIdSchema>;

export const MEDIA_TYPES = ['video', 'image'] as const;
export const mediaTypeSchema = z.enum(MEDIA_TYPES);
export type MediaType = z.infer<typeof mediaTypeSchema>;
