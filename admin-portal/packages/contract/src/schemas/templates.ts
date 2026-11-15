import { z } from 'zod';
import { type ZoneId, intSchema, templateIdSchema, zoneIdSchema } from './common.js';

/** One entry of `android-tv/contract/templates.json` (C6). */
export const templateSpecSchema = z.strictObject({
  id: templateIdSchema,
  /** Supported when `devices/{uid}.info.appVersionCode >= minVersionCode`. */
  minVersionCode: intSchema.positive(),
  /** Which zone forms the portal shows. */
  zones: z.array(zoneIdSchema).min(1),
});
export type TemplateSpec = z.infer<typeof templateSpecSchema>;

export const templatesFileSchema = z.array(templateSpecSchema).superRefine((list, ctx) => {
  const seen = new Set<string>();
  list.forEach((t, i) => {
    if (seen.has(t.id))
      ctx.addIssue({ code: 'custom', path: [i, 'id'], message: `duplicate ${t.id}` });
    seen.add(t.id);
    if (new Set(t.zones).size !== t.zones.length) {
      ctx.addIssue({ code: 'custom', path: [i, 'zones'], message: 'duplicate zone' });
    }
  });
});

/** Templates a box with this versionCode supports. */
export function supportedTemplates(
  templates: readonly TemplateSpec[],
  appVersionCode: number,
): TemplateSpec[] {
  return templates.filter((t) => appVersionCode >= t.minVersionCode);
}

/** Zones a screen configures that its template does not show (the portal only offers the template's zones). */
export function zonesOutsideTemplate(
  template: TemplateSpec,
  zones: Readonly<Partial<Record<ZoneId, unknown>>>,
): ZoneId[] {
  return (Object.keys(zones) as ZoneId[]).filter((z) => !template.zones.includes(z));
}
