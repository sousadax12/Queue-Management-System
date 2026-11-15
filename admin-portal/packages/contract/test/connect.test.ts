import { describe, expect, it } from 'vitest';
import { DEFAULT_EMULATOR_HOST, DEMO_PROJECT_ID, resolveTarget } from '../src/admin/connect.js';

describe('resolveTarget', () => {
  it('defaults to the emulator', () => {
    expect(resolveTarget({ env: {} })).toEqual({
      kind: 'emulator',
      projectId: DEMO_PROJECT_ID,
      host: DEFAULT_EMULATOR_HOST,
    });
    expect(resolveTarget({ env: { FIRESTORE_EMULATOR_HOST: '127.0.0.1:9999' } })).toMatchObject({
      kind: 'emulator',
      host: '127.0.0.1:9999',
    });
  });

  it('needs an explicit project for a real one, and never with the emulator env set', () => {
    expect(resolveTarget({ project: 'qms-dev', env: {} })).toEqual({
      kind: 'project',
      projectId: 'qms-dev',
    });
    expect(() =>
      resolveTarget({ project: 'qms-dev', env: { FIRESTORE_EMULATOR_HOST: 'x:1' } }),
    ).toThrow();
    expect(() =>
      resolveTarget({ project: 'qms-dev', env: { FIREBASE_AUTH_EMULATOR_HOST: 'x:1' } }),
    ).toThrow(/FIREBASE_AUTH_EMULATOR_HOST/);
    expect(() => resolveTarget({ project: 'demo-qms', env: {} })).toThrow(/demo/);
  });

  it('refuses production unless explicitly allowed', () => {
    expect(() => resolveTarget({ project: 'santos-1b6f6', env: {} })).toThrow(/production/);
    expect(resolveTarget({ project: 'santos-1b6f6', allowProduction: true, env: {} })).toEqual({
      kind: 'project',
      projectId: 'santos-1b6f6',
    });
  });
});
