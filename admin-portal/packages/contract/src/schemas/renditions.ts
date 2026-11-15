import { z } from 'zod';
import { httpsUrlSchema, intSchema } from './common.js';

export const sha256Schema = z.string().regex(/^[0-9a-f]{64}$/, 'lowercase hex SHA-256');

/**
 * One immutable CDN object (TV invariant 1). `sha256` is the hex SHA-256 of exactly
 * the bytes behind `url`, computed server side. `codecs` (RFC 6381) is required for video.
 */
export const renditionSchema = z.strictObject({
  url: httpsUrlSchema,
  sha256: sha256Schema,
  sizeBytes: intSchema.positive(),
  mimeType: z.string().regex(/^[a-z]+\/[a-z0-9.+-]+$/),
  codecs: z.string().min(1).optional(),
  width: intSchema.positive(),
  height: intSchema.positive(),
});
export type Rendition = z.infer<typeof renditionSchema>;

/** TV invariant 2: a video needs a rendition the baseline decoder plays (H.264, height <= 1080). */
export function hasBaselineVideoRendition(renditions: readonly Rendition[]): boolean {
  return renditions.some((r) => r.height <= 1080 && (r.codecs ?? '').startsWith('avc1.'));
}
