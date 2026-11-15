import { describe, expect, it } from 'vitest';
import {
  SERVER_TIMESTAMP,
  assertEncoding,
  parseEncodedJson,
  resolveSentinels,
  timestampFromParts,
  timestampParts,
  timestampSchema,
  timestampToMillis,
  writeTimestampSchema,
} from '../src/index.js';

describe('number encoding', () => {
  it('keeps integers and non-integral doubles', () => {
    expect(parseEncodedJson('{"a": 1, "b": 0.5, "c": -3, "d": 4294967295}')).toEqual({
      a: 1,
      b: 0.5,
      c: -3,
      d: 4294967295,
    });
  });

  it.each(['1.0', '2.50e1', '1e3', '-0.0'])(
    'rejects the ambiguous integer-valued double %s',
    (lit) => {
      expect(() => parseEncodedJson(`{"volume": ${lit}}`)).toThrow(/integer-valued doubles/);
    },
  );

  it('does not mistake numbers inside strings', () => {
    expect(parseEncodedJson('{"appVersion": "0.6.0 (21)"}')).toEqual({ appVersion: '0.6.0 (21)' });
  });
});

describe('timestamp encoding', () => {
  it('accepts UTC ISO-8601 with 0 to 6 fraction digits', () => {
    for (const s of [
      '2026-11-15T10:00:00Z',
      '2026-11-15T10:00:00.5Z',
      '2026-11-15T10:00:00.123456Z',
    ]) {
      expect(timestampSchema.safeParse({ __ts: s }).success, s).toBe(true);
    }
  });

  it.each([
    '2026-11-15T10:00:00+01:00',
    '2026-11-15 10:00:00Z',
    '2026-11-15',
    '2026-13-40T10:00:00Z',
    '2026-11-15T10:00:00.1234567Z',
  ])('rejects %s', (s) => {
    expect(timestampSchema.safeParse({ __ts: s }).success).toBe(false);
  });

  it('round-trips seconds and nanoseconds', () => {
    const t = { __ts: '2026-11-15T09:58:12.123456Z' };
    const parts = timestampParts(t);
    expect(parts).toEqual({ seconds: 1794736692, nanoseconds: 123456000 });
    expect(timestampFromParts(parts.seconds, parts.nanoseconds)).toEqual(t);
    expect(timestampFromParts(parts.seconds, 500_000_000)).toEqual({
      __ts: '2026-11-15T09:58:12.500Z',
    });
    expect(timestampFromParts(parts.seconds, 0)).toEqual({ __ts: '2026-11-15T09:58:12Z' });
    expect(timestampToMillis(t)).toBe(1794736692123);
    expect(timestampFromParts(parts.seconds, 123456789)).toEqual(t); // sub-microsecond dropped
  });

  it('write timestamps take either form', () => {
    expect(writeTimestampSchema.safeParse(SERVER_TIMESTAMP).success).toBe(true);
    expect(writeTimestampSchema.safeParse({ __ts: '2026-11-15T10:00:00Z' }).success).toBe(true);
    expect(writeTimestampSchema.safeParse({ __sentinel: 'delete' }).success).toBe(false);
    expect(writeTimestampSchema.safeParse('2026-11-15T10:00:00Z').success).toBe(false);
  });
});

describe('reserved keys', () => {
  it.each([
    '{"x": {"__tz": "2026-11-15T10:00:00Z"}}',
    '{"x": {"__sentinel": "increment"}}',
    '{"x": [{"__ts": "2026-11-15T10:00:00Z", "extra": 1}]}',
  ])('rejects %s', (json) => {
    expect(() => parseEncodedJson(json)).toThrow(/reserved "__" key/);
  });

  it('accepts the two encodings anywhere', () => {
    expect(() => {
      assertEncoding({ a: [{ b: { __ts: '2026-11-15T10:00:00Z' } }], c: SERVER_TIMESTAMP });
    }).not.toThrow();
  });
});

describe('resolveSentinels', () => {
  it('replaces sentinels with now and leaves the rest', () => {
    const now = { __ts: '2026-11-15T10:00:00Z' };
    const doc = {
      a: SERVER_TIMESTAMP,
      b: [SERVER_TIMESTAMP],
      c: { __ts: '2020-01-01T00:00:00Z' },
      d: 1,
    };
    expect(resolveSentinels(doc, now)).toEqual({ a: now, b: [now], c: doc.c, d: 1 });
  });
});
