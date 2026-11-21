import { describe, expect, it } from 'vitest';
import {
  NATIVE_QUEUE_DEFAULTS,
  auditQueue,
  classifyVideoUrl,
  contrastRatio,
  formatArgb,
} from '../src/audit/queues.js';
import {
  type AuditReport,
  exitCodeOf,
  needsAttention,
  renderReport,
  reportToJson,
} from '../src/audit/report.js';
import { loadCollectionFixtures, loadLegacyQueueFixtures } from '../src/fixtures.js';

const fixtures = { ...loadCollectionFixtures('queues'), ...loadLegacyQueueFixtures() };
const audit = (id: string) => auditQueue(id, fixtures[`queues/${id}`] as Record<string, unknown>);
const codes = (row: ReturnType<typeof auditQueue>, severity = 'attention') =>
  row.findings.filter((f) => f.severity === severity).map((f) => f.code);

/** A complete, backfilled queue; tests override one field at a time. */
const GOOD = {
  orgId: 'org_a',
  siteId: 'site_a',
  name: 'Balcão',
  number: 1,
  icon: 0xf007,
  backgroundColor: 0xff2d5b9f,
  foregroundColor: 0xffffffff,
  videoURL: '',
  videoVolume: 0,
  soundVolume: 1,
  velocity: 60,
};
const withFields = (over: Record<string, unknown>) => auditQueue('q', { ...GOOD, ...over });

describe('colours', () => {
  it('formats ARGB and computes WCAG contrast', () => {
    expect(formatArgb(0xff2d5b9f)).toBe('#FF2D5B9F');
    expect(formatArgb(0x00000001)).toBe('#00000001');
    expect(contrastRatio(0xffffffff, 0xff000000)).toBeCloseTo(21, 5);
    expect(contrastRatio(0xff000000, 0xffffffff)).toBeCloseTo(21, 5);
    expect(contrastRatio(0xff808080, 0xff808080)).toBeCloseTo(1, 5);
  });

  it('blends a translucent foreground over the background', () => {
    expect(contrastRatio(0x00ffffff, 0xff000000)).toBeCloseTo(1, 5);
    const half = contrastRatio(0x80ffffff, 0xff000000);
    expect(half).toBeGreaterThan(1);
    expect(half).toBeLessThan(21);
  });
});

describe('classifyVideoUrl', () => {
  it.each([
    ['', 'empty'],
    ['   ', 'empty'],
    ['https://www.youtube.com/watch?v=aqz-KE-bpKQ', 'youtube'],
    ['http://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ', 'youtube'],
    ['https://youtu.be/dQw4w9WgXcQ?t=3', 'youtube'],
    ['https://www.youtube.com/playlist?list=PL123', 'youtube-playlist'],
    ['https://www.youtube.com/watch?v=short', 'youtube-no-id'],
    ['https://youtu.be/', 'youtube-no-id'],
    ['https://cdn.example.com/loop.mp4?token=a%20b', 'mp4'],
    ['https://cdn.example.com/LOOP.MP4', 'mp4'],
    ['https://cdn.example.com/live/index.m3u8', 'hls-dash'],
    ['https://cdn.example.com/live/manifest.mpd', 'hls-dash'],
    [' http://10.0.2.2:8000/loop.mp4 ', 'http'],
    ['http://cdn.example.com/live/index.m3u8', 'http'],
    ['https://cdn.example.com/stream', 'other'],
    ['https://notyoutube.com/watch?v=dQw4w9WgXcQ', 'other'],
    ['rtsp://camera.local/stream', 'malformed'],
    ['loop.mp4', 'malformed'],
    ['http://exa mple.com/a.mp4', 'malformed'],
  ])('%j is %s', (url, expected) => {
    expect(classifyVideoUrl(url)).toBe(expected);
  });
});

describe('auditQueue on the contract fixtures', () => {
  it('passes a complete queue with white text', () => {
    const row = audit('q_balcao_a_P4tc');
    expect(row.findings).toEqual([]);
    expect(needsAttention(row)).toBe(false);
    expect(row.details).toMatchObject({
      name: 'Balcão A',
      foregroundColor: '#FFFFFFFF',
      foregroundColorSource: 'doc',
      videoClass: 'empty',
      missingFields: [],
    });
  });

  it('notes a missing foregroundColor without asking for attention', () => {
    const row = audit('q_balcao_b_Lr6w');
    expect(needsAttention(row)).toBe(false);
    expect(codes(row, 'info')).toEqual(['foreground-missing']);
    expect(row.details).toMatchObject({ foregroundColorSource: 'default' });
    expect(row.cells.text).toBe('#FFFFFFFF (default)');
  });

  it('flags text that is not white: Flutter painted it white', () => {
    const row = audit('q_consultas_Ma9v');
    expect(codes(row)).toEqual(['foreground-not-white']);
    expect(row.details).toMatchObject({ foregroundColor: '#FF000000' });
  });

  it('notes low contrast that Flutter already has (white on orange)', () => {
    const row = audit('q_analises_Ce7y');
    expect(needsAttention(row)).toBe(false);
    expect(codes(row, 'info')).toEqual(['foreground-missing', 'low-contrast']);
  });

  it('flags Flutter-era queues that were not backfilled', () => {
    expect(codes(audit('legacy_balcao_1'))).toEqual(['missing-org-site']);
    expect(audit('legacy_balcao_1').details).toMatchObject({ videoClass: 'youtube' });
    expect(audit('legacy_balcao_1').cells.tenant).toBe('missing orgId, siteId');
  });

  it('lists the fields a hand-made queue is missing, with the native defaults', () => {
    const row = audit('legacy_manual_3');
    expect(codes(row)).toEqual(['missing-fields', 'missing-org-site']);
    expect(row.details).toMatchObject({
      missingFields: ['icon', 'videoURL', 'videoVolume', 'velocity'],
      invalidFields: [],
    });
    const message = row.findings.find((f) => f.code === 'missing-fields')?.message;
    expect(message).toContain('icon (default 0xf007)');
    expect(message).toContain('velocity (default 60)');
  });
});

describe('auditQueue field handling', () => {
  it('uses the native defaults, which extend the contract defaults with an empty name', () => {
    expect(NATIVE_QUEUE_DEFAULTS).toMatchObject({ name: '', icon: 0xf007, soundVolume: 1 });
    const row = auditQueue('q', { orgId: 'o', siteId: 's' });
    expect(row.details).toMatchObject({
      name: '',
      backgroundColor: '#FF2D5B9F',
      foregroundColor: '#FFFFFFFF',
      videoClass: 'empty',
    });
    expect((row.details?.missingFields as string[]).length).toBe(8);
  });

  it('flags low contrast caused by a non-white foregroundColor', () => {
    const row = withFields({ foregroundColor: 0xff2d5b9f });
    expect(codes(row)).toEqual(['foreground-not-white', 'low-contrast']);
  });

  it('keeps the bit pattern of a signed ARGB value', () => {
    const row = withFields({ foregroundColor: -1, backgroundColor: -16777216 });
    expect(row.findings).toEqual([]);
    expect(row.details).toMatchObject({
      foregroundColor: '#FFFFFFFF',
      backgroundColor: '#FF000000',
    });
  });

  it('flags mistyped and out-of-range fields the native app would default or clamp', () => {
    const row = withFields({
      icon: 'user',
      number: 1.5,
      velocity: -5,
      videoVolume: 3,
      foregroundColor: 2 ** 40,
      videoURL: null,
    });
    expect(row.details).toMatchObject({
      missingFields: ['videoURL'],
      invalidFields: ['number', 'icon', 'videoVolume', 'velocity', 'foregroundColor'],
    });
    const messages = row.findings.map((f) => f.message).join('\n');
    expect(messages).toContain('icon: expected a finite number, got string "user"');
    expect(messages).toContain('videoVolume: 3 is outside 0..1 (clamped to 1).');
    expect(messages).toContain('velocity: -5 is negative; the native app uses its default (60).');
  });

  it.each([
    ['http://cdn.example.com/loop.mp4', 'video-http'],
    ['https://cdn.example.com/live.m3u8', 'video-hls-dash'],
    ['https://cdn.example.com/stream', 'video-other'],
    ['not a url', 'video-malformed'],
  ])('flags videoURL %j as %s', (videoURL, code) => {
    expect(codes(withFields({ videoURL }))).toEqual([code]);
  });

  it('accepts https MP4 and YouTube videos', () => {
    expect(withFields({ videoURL: 'https://cdn.example.com/loop.mp4' }).findings).toEqual([]);
    expect(withFields({ videoURL: 'https://youtu.be/dQw4w9WgXcQ' }).findings).toEqual([]);
  });

  it('treats an empty orgId or siteId as missing', () => {
    const row = withFields({ siteId: '' });
    expect(codes(row)).toEqual(['missing-org-site']);
    expect(row.cells.tenant).toBe('missing siteId');
  });
});

describe('report', () => {
  const ids = ['q_balcao_a_P4tc', 'q_consultas_Ma9v', 'legacy_manual_3'];
  const report: AuditReport = {
    target: 'Firestore emulator 127.0.0.1:8080 (project demo-qms)',
    sections: [
      {
        id: 'queues',
        title: 'Queues',
        columns: [{ key: 'name', header: 'NAME' }],
        rows: ids.map(audit),
      },
    ],
  };

  it('exits 1 only when a row needs attention', () => {
    expect(exitCodeOf(report)).toBe(1);
    const clean: AuditReport = {
      target: 't',
      sections: [
        { ...report.sections[0]!, rows: [audit('q_balcao_a_P4tc'), audit('q_balcao_b_Lr6w')] },
      ],
    };
    expect(exitCodeOf(clean)).toBe(0);
    expect(renderReport(clean)).toContain('Result: nothing needs attention.');
    expect(exitCodeOf({ target: 't', sections: [] })).toBe(0);
  });

  it('renders an aligned table and the findings', () => {
    const text = renderReport(report);
    expect(text).toContain('== Queues: 3 checked, 2 need attention ==');
    expect(text).toMatch(/^STATUS {5}ID {16}NAME$/m);
    expect(text).toMatch(/^ok {9}q_balcao_a_P4tc {3}Balcão A$/m);
    expect(text).toMatch(/^ATTENTION {2}q_consultas_Ma9v {2}Consultas$/m);
    expect(text).toContain('  [!] foregroundColor is #FF000000, not white');
    expect(text).toContain('Result: 2 items need attention before the cutover.');
  });

  it('renders every section, so a second one needs no change here', () => {
    const two: AuditReport = {
      target: 't',
      sections: [
        ...report.sections,
        {
          id: 'accounts',
          title: 'Accounts',
          columns: [{ key: 'email', header: 'EMAIL' }],
          rows: [
            {
              id: 'uid_1',
              cells: { email: 'a@example.com' },
              findings: [{ code: 'no-claims', severity: 'attention', message: 'No claims.' }],
              details: { email: 'a@example.com' },
            },
          ],
        },
      ],
    };
    expect(renderReport(two)).toContain('== Accounts: 1 checked, 1 need attention ==');
    expect(renderReport(two)).toContain('Result: 3 items need attention');
    expect(reportToJson(two)).toMatchObject({
      attention: 3,
      sections: [
        { id: 'queues' },
        { id: 'accounts', rows: [{ id: 'uid_1', status: 'attention' }] },
      ],
    });
  });

  it('gives machine-readable output', () => {
    expect(reportToJson(report)).toMatchObject({
      target: report.target,
      attention: 2,
      sections: [
        {
          id: 'queues',
          attention: 2,
          rows: [
            { id: 'q_balcao_a_P4tc', status: 'ok', videoClass: 'empty', findings: [] },
            { id: 'q_consultas_Ma9v', status: 'attention', foregroundColor: '#FF000000' },
            { id: 'legacy_manual_3', status: 'attention', orgId: null },
          ],
        },
      ],
    });
  });
});
