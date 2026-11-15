import { z } from 'zod';
import { nonNegativeIntSchema, optionalAuditFields, writeTimestampSchema } from './common.js';

export const ORG_STATUSES = ['active', 'suspended'] as const;

/** `orgs/{orgId}`: portal-only (C10). The TV never reads it. */
export const orgSchema = z.strictObject({
  name: z.string().min(1),
  status: z.enum(ORG_STATUSES),
  createdAt: writeTimestampSchema,
  maxSites: nonNegativeIntSchema.optional(),
  maxDevices: nonNegativeIntSchema.optional(),
  maxMediaBytes: nonNegativeIntSchema.optional(),
  ...optionalAuditFields,
});
export type OrgDoc = z.infer<typeof orgSchema>;
