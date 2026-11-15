import { z } from 'zod';
import {
  docIdSchema,
  httpsUrlSchema,
  intSchema,
  mediaTypeSchema,
  optionalAuditFields,
  timestampSchema,
  uidSchema,
  writeTimestampSchema,
} from './common.js';
import { renditionSchema } from './renditions.js';

/** Pipeline state (C1). */
export const MEDIA_STATUSES = ['uploading', 'processing', 'ready', 'failed', 'deleted'] as const;
export const mediaStatusSchema = z.enum(MEDIA_STATUSES);

/** What was uploaded, before processing. */
export const mediaSourceSchema = z.strictObject({
  fileName: z.string().min(1),
  sizeBytes: intSchema.nonnegative(),
  mimeType: z.string().min(1).optional(),
  /** Free-form ffprobe/sharp summary for the portal UI. */
  probe: z.record(z.string(), z.unknown()).optional(),
});

/**
 * `media/{mediaId}`: the portal's library, written only by media-api (Admin SDK).
 * The TV never reads it. `siteId: null` = org-wide.
 */
export const mediaSchema = z
  .strictObject({
    orgId: docIdSchema,
    siteId: docIdSchema.nullable(),
    name: z.string().min(1),
    type: mediaTypeSchema,
    /** Empty until `ready`. */
    renditions: z.array(renditionSchema),
    durationMs: intSchema.positive().nullable(),
    /** Sum of rendition sizes, for per-org usage. */
    sizeBytes: intSchema.nonnegative().optional(),
    tags: z.array(z.string().min(1)),
    uploadedBy: uidSchema,
    createdAt: writeTimestampSchema,
    status: mediaStatusSchema,
    /** Human-readable rejection or failure reason. Null unless `failed`. */
    error: z.string().nullable(),
    source: mediaSourceSchema,
    posterUrl: httpsUrlSchema.nullable(),
    /** Set while a worker holds the job, so a stale job can be picked up again. */
    processingAt: timestampSchema.nullable(),
    ...optionalAuditFields,
  })
  .superRefine((m, ctx) => {
    if (m.status === 'ready' && m.renditions.length === 0) {
      ctx.addIssue({
        code: 'custom',
        path: ['renditions'],
        message: 'ready media needs renditions',
      });
    }
    if (m.status === 'ready' && m.type === 'video' && m.durationMs === null) {
      ctx.addIssue({
        code: 'custom',
        path: ['durationMs'],
        message: 'ready video needs durationMs',
      });
    }
    if (m.status === 'failed' && !m.error) {
      ctx.addIssue({ code: 'custom', path: ['error'], message: 'failed media needs an error' });
    }
  });
export type MediaDoc = z.infer<typeof mediaSchema>;
