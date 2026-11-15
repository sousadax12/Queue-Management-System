import { z } from 'zod';
import { auditFields, docIdSchema, intSchema, uidSchema } from './common.js';
import { hasBaselineVideoRendition, renditionSchema } from './renditions.js';

const videoItemSchema = z.strictObject({
  mediaId: docIdSchema,
  type: z.literal('video'),
  /** Null = play to the end. */
  durationSec: z.number().positive().nullable(),
  renditions: z.array(renditionSchema).min(1),
  durationMs: intSchema.positive(),
});

const imageItemSchema = z.strictObject({
  mediaId: docIdSchema,
  type: z.literal('image'),
  durationSec: z.number().positive(),
  renditions: z.array(renditionSchema).min(1),
});

/** A playlist item: a snapshot copied from a `ready` media doc (TV invariants 2 and 3). */
export const playlistItemSchema = z
  .discriminatedUnion('type', [videoItemSchema, imageItemSchema])
  .superRefine((item, ctx) => {
    if (item.type !== 'video') return;
    item.renditions.forEach((r, i) => {
      if (!r.codecs) {
        ctx.addIssue({
          code: 'custom',
          path: ['renditions', i, 'codecs'],
          message: 'required for video',
        });
      }
    });
    if (!hasBaselineVideoRendition(item.renditions)) {
      ctx.addIssue({
        code: 'custom',
        path: ['renditions'],
        message: 'needs at least one H.264 (avc1) rendition with height <= 1080',
      });
    }
  });
export type PlaylistItem = z.infer<typeof playlistItemSchema>;

/**
 * `playlists/{playlistId}`, written by the portal in one atomic doc write (TV invariant 4).
 * `siteId: null` = shared by every site of the same org (C10). `mediaIds` mirrors
 * `items[].mediaId` for `array-contains` queries (C2).
 */
export const playlistSchema = z
  .strictObject({
    orgId: docIdSchema,
    name: z.string().min(1),
    siteId: docIdSchema.nullable(),
    mediaIds: z.array(docIdSchema),
    items: z.array(playlistItemSchema),
    publishedBy: uidSchema.optional(),
    ...auditFields,
  })
  .superRefine((p, ctx) => {
    const fromItems = [...new Set(p.items.map((i) => i.mediaId))].sort();
    const mirrored = [...p.mediaIds].sort();
    if (new Set(p.mediaIds).size !== p.mediaIds.length) {
      ctx.addIssue({ code: 'custom', path: ['mediaIds'], message: 'must not repeat ids' });
    }
    if (JSON.stringify(fromItems) !== JSON.stringify(mirrored)) {
      ctx.addIssue({
        code: 'custom',
        path: ['mediaIds'],
        message: `must mirror items[].mediaId (expected ${JSON.stringify(fromItems)})`,
      });
    }
  });
export type PlaylistDoc = z.infer<typeof playlistSchema>;
