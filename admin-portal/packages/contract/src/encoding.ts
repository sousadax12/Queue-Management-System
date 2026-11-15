/**
 * The one JSON encoding for Firestore values that JSON cannot express
 * (see fixtures/README.md). Schemas validate this encoded form, the fixture
 * loader parses it, and the Admin SDK codec converts it to and from Firestore.
 *
 * - `{"__ts": "2026-11-15T10:00:00Z"}`        a Firestore Timestamp (ISO-8601, UTC, `Z`, 0-6 fraction digits:
 *                                               Firestore stores microseconds)
 * - `{"__sentinel": "serverTimestamp"}`        a `serverTimestamp()` write placeholder
 * - JSON numbers: a number written without `.` or exponent is a Firestore integer (Long),
 *   anything else is a double. Integer-valued doubles such as `1.0` are not allowed in
 *   fixtures because JavaScript cannot keep them apart from `1` (TV invariant 11).
 */
import { z } from 'zod';

export const TS_KEY = '__ts';
export const SENTINEL_KEY = '__sentinel';

export interface EncodedTimestamp {
  __ts: string;
}

export interface ServerTimestampSentinel {
  __sentinel: 'serverTimestamp';
}

export const SERVER_TIMESTAMP: ServerTimestampSentinel = Object.freeze({
  __sentinel: 'serverTimestamp',
});

const ISO_UTC = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/;

/** A resolved Firestore Timestamp, as stored and as read. */
export const timestampSchema = z.strictObject({
  __ts: z
    .string()
    .regex(
      ISO_UTC,
      'must be ISO-8601 UTC with a Z suffix and at most 6 fraction digits, e.g. 2026-11-15T10:00:00Z',
    )
    .refine((s) => !Number.isNaN(Date.parse(s)), 'not a valid date'),
});

export const serverTimestampSchema = z.strictObject({
  __sentinel: z.literal('serverTimestamp'),
});

/** A field the writer sets with `serverTimestamp()`: a sentinel on write, a timestamp on read. */
export const writeTimestampSchema = z.union([timestampSchema, serverTimestampSchema]);

export type WriteTimestamp = EncodedTimestamp | ServerTimestampSentinel;

export function isEncodedTimestamp(v: unknown): v is EncodedTimestamp {
  return timestampSchema.safeParse(v).success;
}

export function isServerTimestamp(v: unknown): v is ServerTimestampSentinel {
  return serverTimestampSchema.safeParse(v).success;
}

/** Build an encoded timestamp from a Date or epoch millis. */
export function ts(value: Date | number): EncodedTimestamp {
  const d = typeof value === 'number' ? new Date(value) : value;
  return { __ts: d.toISOString() };
}

/** Seconds and nanoseconds of an encoded timestamp, without losing sub-millisecond digits. */
export function timestampParts(t: EncodedTimestamp): { seconds: number; nanoseconds: number } {
  const m = ISO_UTC.exec(t.__ts);
  if (!m?.[1]) throw new Error(`Invalid encoded timestamp: ${t.__ts}`);
  const seconds = Date.parse(`${m[1]}Z`) / 1000;
  const nanoseconds = Number((m[2] ?? '').padEnd(9, '0'));
  return { seconds, nanoseconds };
}

export function timestampToMillis(t: EncodedTimestamp): number {
  const { seconds, nanoseconds } = timestampParts(t);
  return seconds * 1000 + Math.floor(nanoseconds / 1e6);
}

/**
 * Encode seconds + nanoseconds: no fraction, 3 digits when millisecond-exact, otherwise
 * 6 digits. Sub-microsecond digits are dropped, as Firestore does.
 */
export function timestampFromParts(seconds: number, nanoseconds: number): EncodedTimestamp {
  const base = new Date(seconds * 1000).toISOString().slice(0, 19);
  const micros = Math.floor(nanoseconds / 1000);
  if (micros === 0) return { __ts: `${base}Z` };
  if (micros % 1000 === 0) return { __ts: `${base}.${String(micros / 1000).padStart(3, '0')}Z` };
  return { __ts: `${base}.${String(micros).padStart(6, '0')}Z` };
}

/**
 * Throws if `value` uses a reserved `__` key in any shape other than the two
 * encodings above, so a typo such as `{"__tz": ...}` fails loudly.
 */
export function assertEncoding(value: unknown, path = '$'): void {
  if (Array.isArray(value)) {
    value.forEach((v, i) => {
      assertEncoding(v, `${path}[${i}]`);
    });
    return;
  }
  if (value === null || typeof value !== 'object') return;
  const keys = Object.keys(value);
  if (keys.some((k) => k.startsWith('__'))) {
    if (isEncodedTimestamp(value) || isServerTimestamp(value)) return;
    throw new Error(
      `${path}: reserved "__" key in ${JSON.stringify(value)}; only {"__ts": "<ISO UTC>"} and {"__sentinel": "serverTimestamp"} are allowed`,
    );
  }
  for (const [k, v] of Object.entries(value)) assertEncoding(v, `${path}.${k}`);
}

/**
 * Replace `serverTimestamp` sentinels with `now`, the way Firestore resolves
 * them. Use it to turn a write fixture into what a reader would see.
 */
export function resolveSentinels<T>(value: T, now: EncodedTimestamp): T {
  if (Array.isArray(value)) return value.map((v: unknown) => resolveSentinels(v, now)) as T;
  if (value === null || typeof value !== 'object') return value;
  if (isServerTimestamp(value)) return { ...now } as T;
  if (isEncodedTimestamp(value)) return value;
  return Object.fromEntries(
    Object.entries(value).map(([k, v]) => [k, resolveSentinels(v, now)]),
  ) as T;
}

type Reviver = (key: string, value: unknown, context?: { source?: string }) => unknown;

/**
 * JSON.parse that rejects integer-valued doubles (`1.0`, `2e3`) because the
 * int/double distinction would be lost, and checks the reserved-key encoding.
 * Relies on JSON.parse source text access (Node 21+).
 */
export function parseEncodedJson(text: string, label = 'JSON'): unknown {
  const offenders: string[] = [];
  const reviver: Reviver = (key, value, context) => {
    if (typeof value === 'number' && context?.source !== undefined) {
      const isIntLiteral = /^-?\d+$/.test(context.source);
      if (!isIntLiteral && Number.isInteger(value)) offenders.push(`${key}: ${context.source}`);
    }
    return value;
  };
  const parsed: unknown = JSON.parse(text, reviver as Parameters<typeof JSON.parse>[1]);
  if (offenders.length > 0) {
    throw new Error(
      `${label}: integer-valued doubles are ambiguous across JS and Kotlin; write them as integers (${offenders.join(', ')})`,
    );
  }
  assertEncoding(parsed);
  return parsed;
}
