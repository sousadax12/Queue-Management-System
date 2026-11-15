import { describe, expect, it } from 'vitest';
import { TEMPLATE_IDS, supportedTemplates, templatesFileSchema } from '../src/index.js';
import { loadTemplates } from '../src/fixtures.js';

describe('android-tv/contract/templates.json (C6)', () => {
  const templates = loadTemplates();

  it('lists exactly the app enum, each at minVersionCode 1', () => {
    expect(templates.map((t) => t.id).sort()).toEqual([...TEMPLATE_IDS].sort());
    expect(templates.every((t) => t.minVersionCode === 1)).toBe(true);
  });

  it('gives queues_media every zone', () => {
    expect(templates.find((t) => t.id === 'queues_media')?.zones.sort()).toEqual([
      'clock',
      'media',
      'queues',
      'ticker',
    ]);
  });

  it('filters by appVersionCode', () => {
    const list = [
      ...templates,
      { id: 'queues_only' as const, minVersionCode: 30, zones: ['queues' as const] },
    ];
    expect(supportedTemplates(list, 21).filter((t) => t.minVersionCode === 30)).toEqual([]);
    expect(supportedTemplates(list, 30)).toHaveLength(list.length);
  });

  it('rejects duplicates, unknown ids and unknown zones', () => {
    expect(templatesFileSchema.safeParse([...templates, templates[0]]).success).toBe(false);
    expect(
      templatesFileSchema.safeParse([{ id: 'weather', minVersionCode: 1, zones: ['media'] }])
        .success,
    ).toBe(false);
    expect(
      templatesFileSchema.safeParse([{ id: 'queues_only', minVersionCode: 1, zones: ['map'] }])
        .success,
    ).toBe(false);
  });
});
