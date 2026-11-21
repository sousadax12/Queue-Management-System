/**
 * What changes for one `queues/{queueId}` doc when a box moves from the Flutter operator app
 * to the native one. Pure: no Firebase, no I/O. It mirrors, by hand, three pieces of the apps:
 *
 * - the native mapper and its forgiving field reads (`android-tv/data/.../mapping/QueueMapper.kt`
 *   and `Fields.kt`): a missing or mistyped field silently takes its `QueueDefaults` value;
 * - the native video URL handling (`android-tv/ui/.../zones/video/LegacyVideo.kt`);
 * - the Flutter mapper (`lib/mappers/queue_doc_mapper.dart`): it ignores `foregroundColor`
 *   (text is always white) and throws when a legacy field is missing.
 */
import { QUEUE_DEFAULTS } from '../schemas/queues.js';
import type { AuditColumn, AuditRow, Finding } from './report.js';

/** The native `QueueDefaults`: the contract's `QUEUE_DEFAULTS` plus an empty name. */
export const NATIVE_QUEUE_DEFAULTS = { name: '', ...QUEUE_DEFAULTS } as const;

const WHITE = 0xffffffff;
/** WCAG 2 minimum for large text; the queue number and name are large. */
export const MIN_CONTRAST = 3;

// ---------------------------------------------------------------------------------------
// Colours (ARGB as an unsigned 32-bit int, like Flutter's `Color.value`)
// ---------------------------------------------------------------------------------------

export const formatArgb = (argb: number): string =>
  `#${argb.toString(16).toUpperCase().padStart(8, '0')}`;

const channels = (argb: number) => ({
  a: ((argb >>> 24) & 0xff) / 255,
  r: (argb >>> 16) & 0xff,
  g: (argb >>> 8) & 0xff,
  b: argb & 0xff,
});

function luminance(r: number, g: number, b: number): number {
  const lin = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

/**
 * WCAG contrast ratio (1..21) of text in `foreground` on `background`. A translucent
 * foreground is blended over the background first; the background's own alpha is ignored.
 */
export function contrastRatio(foreground: number, background: number): number {
  const fg = channels(foreground);
  const bg = channels(background);
  const mix = (f: number, b: number) => f * fg.a + b * (1 - fg.a);
  const l1 = luminance(mix(fg.r, bg.r), mix(fg.g, bg.g), mix(fg.b, bg.b));
  const l2 = luminance(bg.r, bg.g, bg.b);
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
}

// ---------------------------------------------------------------------------------------
// Video URL
// ---------------------------------------------------------------------------------------

export type VideoClass =
  | 'empty'
  | 'youtube'
  | 'mp4'
  | 'hls-dash'
  | 'http'
  | 'other'
  | 'malformed'
  | 'youtube-playlist'
  | 'youtube-no-id';

const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;
const isHost = (host: string, domain: string) => host === domain || host.endsWith(`.${domain}`);

/**
 * Classifies a `videoURL` the way the native app treats it. YouTube links are checked first
 * (an `http://` YouTube link is fine: only the video id is used), then cleartext, then the
 * file extension. `URL` is a little more lenient than the `java.net.URI` the app uses, so a
 * URL with whitespace inside is rejected here by hand.
 */
export function classifyVideoUrl(raw: string): VideoClass {
  const trimmed = raw.trim();
  if (trimmed === '') return 'empty';
  if (/\s/.test(trimmed) || !URL.canParse(trimmed)) return 'malformed';
  const url = new URL(trimmed);
  const host = url.hostname.toLowerCase();
  if ((url.protocol !== 'http:' && url.protocol !== 'https:') || host === '') return 'malformed';

  let videoId: string | null | undefined;
  if (isHost(host, 'youtube.com')) videoId = url.searchParams.get('v');
  else if (isHost(host, 'youtu.be')) videoId = url.pathname.split('/').find((s) => s !== '');
  else {
    if (url.protocol === 'http:') return 'http';
    const path = url.pathname.toLowerCase();
    if (path.endsWith('.mp4') || path.endsWith('.m4v')) return 'mp4';
    if (path.endsWith('.m3u8') || path.endsWith('.mpd')) return 'hls-dash';
    return 'other';
  }
  if (!videoId && url.searchParams.has('list')) return 'youtube-playlist';
  return videoId && YOUTUBE_ID.test(videoId) ? 'youtube' : 'youtube-no-id';
}

const VIDEO_FINDINGS: Partial<Record<VideoClass, string>> = {
  http: 'videoURL is http://: release builds of the native app block cleartext traffic, so the video stays black. Switch it to https:// or YouTube.',
  'hls-dash':
    'videoURL is an HLS/DASH stream (.m3u8/.mpd): check that the native build you install plays streams (known differences in docs/operator/DEPLOY.md) and test this video on the pilot box.',
  other:
    'videoURL is a direct link that is not .mp4, .m3u8 or .mpd: the format is unknown, so test this video on the pilot box.',
  malformed: 'videoURL is not an absolute http(s) URL: the native app shows no video.',
  'youtube-playlist':
    'videoURL is a YouTube playlist without a video: the native app shows no video (Flutter rejected it too).',
  'youtube-no-id':
    'videoURL is a YouTube link without a valid 11-character video id: the native app shows no video.',
};

// ---------------------------------------------------------------------------------------
// Field reads, as `Fields.kt` does them
// ---------------------------------------------------------------------------------------

/** `value` is what the native app ends up with; `problem` says why a present value was not kept as is. */
type Read<T> = { missing: true } | { missing: false; value?: T; problem?: string };

const describe = (v: unknown): string =>
  typeof v === 'string'
    ? `string "${v.slice(0, 40)}"`
    : typeof v === 'number' || typeof v === 'boolean'
      ? `${typeof v} ${v}`
      : Array.isArray(v)
        ? 'a list'
        : 'an object';

const absent = (v: unknown) => v === undefined || v === null;

function readString(v: unknown): Read<string> {
  if (absent(v)) return { missing: true };
  return typeof v === 'string'
    ? { missing: false, value: v }
    : { missing: false, problem: `expected a string, got ${describe(v)}` };
}

function readNumber(v: unknown): Read<number> {
  if (absent(v)) return { missing: true };
  return typeof v === 'number' && Number.isFinite(v)
    ? { missing: false, value: v }
    : { missing: false, problem: `expected a finite number, got ${describe(v)}` };
}

function readInteger(v: unknown, min: number, max: number, what: string): Read<number> {
  const n = readNumber(v);
  if (n.missing || n.value === undefined) return n;
  if (!Number.isInteger(n.value) || n.value < min || n.value > max) {
    return { missing: false, problem: `${n.value} is not ${what}` };
  }
  return n;
}

const readInt = (v: unknown) => readInteger(v, -(2 ** 31), 2 ** 31 - 1, 'a 32-bit integer');

/** An unsigned 32-bit ARGB value; a signed one keeps its bit pattern. */
function readArgb(v: unknown): Read<number> {
  const n = readInteger(v, -(2 ** 31), 0xffffffff, 'a 32-bit ARGB colour');
  return n.missing || n.value === undefined ? n : { missing: false, value: n.value >>> 0 };
}

/** A 0..1 gain; the native app clamps anything outside. */
function readGain(v: unknown): Read<number> {
  const n = readNumber(v);
  if (n.missing || n.value === undefined) return n;
  if (n.value < 0 || n.value > 1) {
    const clamped = Math.min(1, Math.max(0, n.value));
    return {
      missing: false,
      value: clamped,
      problem: `${n.value} is outside 0..1 (clamped to ${clamped})`,
    };
  }
  return n;
}

function readVelocity(v: unknown): Read<number> {
  const n = readNumber(v);
  if (n.missing || n.value === undefined) return n;
  return n.value < 0 ? { missing: false, problem: `${n.value} is negative` } : n;
}

// ---------------------------------------------------------------------------------------
// One queue
// ---------------------------------------------------------------------------------------

/** Legacy fields the Flutter mapper reads, with how the native default is shown in the report. */
const LEGACY_READS: {
  field: keyof typeof NATIVE_QUEUE_DEFAULTS;
  read: (v: unknown) => Read<string | number>;
  show: (v: never) => string;
}[] = [
  { field: 'name', read: readString, show: (v: string) => JSON.stringify(v) },
  { field: 'number', read: readInt, show: String },
  { field: 'icon', read: readInt, show: (v: number) => `0x${v.toString(16)}` },
  { field: 'backgroundColor', read: readArgb, show: formatArgb },
  { field: 'videoURL', read: readString, show: (v: string) => JSON.stringify(v) },
  { field: 'videoVolume', read: readGain, show: String },
  { field: 'soundVolume', read: readGain, show: String },
  { field: 'velocity', read: readVelocity, show: String },
];

const showDefault = (field: keyof typeof NATIVE_QUEUE_DEFAULTS): string => {
  const entry = LEGACY_READS.find((r) => r.field === field);
  return (
    (entry?.show as ((v: unknown) => string) | undefined)?.(NATIVE_QUEUE_DEFAULTS[field]) ?? ''
  );
};

export interface QueueAuditDetails extends Record<string, unknown> {
  name: string;
  /** What the native app will use, after defaults. */
  backgroundColor: string;
  foregroundColor: string;
  foregroundColorSource: 'doc' | 'default';
  contrast: number;
  videoClass: VideoClass;
  orgId: string | null;
  siteId: string | null;
  /** Legacy fields absent from the doc; the native app uses its default for each. */
  missingFields: string[];
  /** Fields present with a wrong type or range; the native app defaults (or clamps) them. */
  invalidFields: string[];
}

export const QUEUE_COLUMNS: AuditColumn[] = [
  { key: 'name', header: 'NAME' },
  { key: 'text', header: 'TEXT COLOUR' },
  { key: 'contrast', header: 'CONTRAST' },
  { key: 'video', header: 'VIDEO' },
  { key: 'tenant', header: 'ORG/SITE' },
  { key: 'defaults', header: 'DEFAULTED FIELDS' },
];

const nonEmptyString = (v: unknown): string | null =>
  typeof v === 'string' && v !== '' ? v : null;

/** Audits one raw `queues` doc, as read from Firestore. */
export function auditQueue(id: string, data: Record<string, unknown>): AuditRow {
  const findings: Finding[] = [];
  const attention = (code: string, message: string) =>
    findings.push({ code, severity: 'attention', message });
  const info = (code: string, message: string) =>
    findings.push({ code, severity: 'info', message });

  // Legacy fields: missing or invalid ones silently take the native default.
  const effective: Record<string, string | number> = { ...NATIVE_QUEUE_DEFAULTS };
  const missingFields: string[] = [];
  const invalidFields: string[] = [];
  for (const { field, read } of LEGACY_READS) {
    const r = read(data[field]);
    if (r.missing) {
      missingFields.push(field);
      continue;
    }
    if (r.value !== undefined) effective[field] = r.value;
    if (r.problem !== undefined) {
      invalidFields.push(field);
      attention(
        'invalid-field',
        r.value !== undefined
          ? `${field}: ${r.problem}.`
          : `${field}: ${r.problem}; the native app uses its default (${showDefault(field)}).`,
      );
    }
  }
  if (missingFields.length > 0) {
    attention(
      'missing-fields',
      `Missing ${missingFields.map((f) => `${f} (default ${showDefault(f as keyof typeof NATIVE_QUEUE_DEFAULTS)})`).join(', ')}: the native app shows these defaults without any error, while the Flutter app cannot read this queue at all. Set the fields in the admin portal.`,
    );
  }

  // Text colour: Flutter always paints white, the native app paints foregroundColor.
  const background = effective.backgroundColor as number;
  const fgRead = readArgb(data.foregroundColor);
  const fgFromDoc = !fgRead.missing && fgRead.value !== undefined;
  const foreground = fgFromDoc ? (fgRead.value as number) : NATIVE_QUEUE_DEFAULTS.foregroundColor;
  if (fgRead.missing) {
    info(
      'foreground-missing',
      'foregroundColor is missing: the native app uses white, as Flutter does.',
    );
  } else if (!fgFromDoc) {
    invalidFields.push('foregroundColor');
    attention(
      'invalid-field',
      `foregroundColor: ${fgRead.problem}; the native app uses white, as Flutter does.`,
    );
  } else if (foreground !== WHITE) {
    attention(
      'foreground-not-white',
      `foregroundColor is ${formatArgb(foreground)}, not white: Flutter ignores it and paints white text, the native app paints this colour, so the tile will look different.`,
    );
  }
  const contrast = contrastRatio(foreground, background);
  if (contrast < MIN_CONTRAST) {
    const text = `Low contrast: text ${formatArgb(foreground)} on background ${formatArgb(background)} is ${contrast.toFixed(1)}:1 (below ${MIN_CONTRAST}:1)`;
    if (foreground === WHITE) {
      info('low-contrast', `${text}. This is how the tile already looks on Flutter.`);
    } else {
      attention('low-contrast', `${text}. The number will be hard to read on the native app.`);
    }
  }

  // Video.
  const videoClass = classifyVideoUrl(effective.videoURL as string);
  const videoFinding = VIDEO_FINDINGS[videoClass];
  if (videoFinding) attention(`video-${videoClass}`, videoFinding);

  // Tenant fields.
  const orgId = nonEmptyString(data.orgId);
  const siteId = nonEmptyString(data.siteId);
  const missingTenant = [orgId === null ? 'orgId' : '', siteId === null ? 'siteId' : ''].filter(
    Boolean,
  );
  if (missingTenant.length > 0) {
    attention(
      'missing-org-site',
      `Missing ${missingTenant.join(' and ')}: operator accounts are scoped by org and site, so the native app cannot see or drive this queue. Run \`pnpm backfill:queues\` (admin-portal) for it first.`,
    );
  }

  const details: QueueAuditDetails = {
    name: effective.name as string,
    backgroundColor: formatArgb(background),
    foregroundColor: formatArgb(foreground),
    foregroundColorSource: fgFromDoc ? 'doc' : 'default',
    contrast: Math.round(contrast * 100) / 100,
    videoClass,
    orgId,
    siteId,
    missingFields,
    invalidFields,
  };
  const defaulted = [...missingFields, ...invalidFields];
  return {
    id,
    cells: {
      name: details.name,
      text: `${details.foregroundColor}${fgFromDoc ? '' : ' (default)'}`,
      contrast: `${contrast.toFixed(1)}:1`,
      video: videoClass,
      tenant: missingTenant.length > 0 ? `missing ${missingTenant.join(', ')}` : 'ok',
      defaults: defaulted.length > 0 ? defaulted.join(', ') : '-',
    },
    findings,
    details,
  };
}
