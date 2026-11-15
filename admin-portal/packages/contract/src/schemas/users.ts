import { z } from 'zod';
import { docIdSchema, optionalAuditFields } from './common.js';

export const ROLES = ['platformAdmin', 'orgAdmin', 'manager', 'operator'] as const;
export const roleSchema = z.enum(ROLES);
export type Role = z.infer<typeof roleSchema>;

/** Custom claims on a staff user's ID token, set by media-api. */
export const customClaimsSchema = z.strictObject({
  role: roleSchema,
  /** Null only for platformAdmin. */
  org: docIdSchema.nullable(),
  /** Sites a manager or operator may act on. Empty for admins. */
  sites: z.array(docIdSchema),
});
export type CustomClaims = z.infer<typeof customClaimsSchema>;

/** `users/{uid}`: mirror of the custom claims, written by media-api only. */
export const userSchema = z
  .strictObject({
    email: z.email(),
    displayName: z.string().nullable(),
    role: roleSchema,
    orgId: docIdSchema.nullable(),
    sites: z.array(docIdSchema),
    ...optionalAuditFields,
  })
  .superRefine((u, ctx) => {
    if (u.role === 'platformAdmin' && u.orgId !== null) {
      ctx.addIssue({ code: 'custom', path: ['orgId'], message: 'platformAdmin has no orgId' });
    }
    if (u.role !== 'platformAdmin' && u.orgId === null) {
      ctx.addIssue({ code: 'custom', path: ['orgId'], message: `${u.role} needs an orgId` });
    }
    if ((u.role === 'manager' || u.role === 'operator') && u.sites.length === 0) {
      ctx.addIssue({
        code: 'custom',
        path: ['sites'],
        message: `${u.role} needs at least one site`,
      });
    }
  });
export type UserDoc = z.infer<typeof userSchema>;
