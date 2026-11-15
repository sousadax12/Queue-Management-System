import { describe, expect, it } from 'vitest';
import { readConfig } from '../src/config';

const base = {
  VITE_FIREBASE_API_KEY: 'key',
  VITE_FIREBASE_AUTH_DOMAIN: 'p.firebaseapp.com',
  VITE_FIREBASE_PROJECT_ID: 'p',
  VITE_FIREBASE_APP_ID: '1:2:web:3',
};

describe('readConfig', () => {
  it('reads the Firebase config and defaults the base URL to the page origin', () => {
    expect(readConfig([undefined, base], 'https://admin.example.com')).toEqual({
      ok: true,
      config: {
        firebase: {
          apiKey: 'key',
          authDomain: 'p.firebaseapp.com',
          projectId: 'p',
          appId: '1:2:web:3',
        },
        baseUrl: 'https://admin.example.com',
      },
    });
  });

  it('lets runtime values win and ignores empty ones', () => {
    const r = readConfig(
      [
        {
          VITE_FIREBASE_PROJECT_ID: 'runtime',
          VITE_FIREBASE_API_KEY: '',
          VITE_PUBLIC_BASE_URL: 'https://x.test/',
        },
        base,
      ],
      'http://localhost',
    );
    expect(r.ok && r.config.firebase.projectId).toBe('runtime');
    expect(r.ok && r.config.firebase.apiKey).toBe('key');
    expect(r.ok && r.config.baseUrl).toBe('https://x.test');
  });

  it('lists what is missing', () => {
    const r = readConfig(
      [{ VITE_FIREBASE_API_KEY: 'k', VITE_PUBLIC_BASE_URL: 'nope' }],
      'http://x',
    );
    expect(r).toEqual({
      ok: false,
      missing: expect.arrayContaining([
        'VITE_FIREBASE_AUTH_DOMAIN',
        'VITE_FIREBASE_PROJECT_ID',
        'VITE_FIREBASE_APP_ID',
        'VITE_PUBLIC_BASE_URL',
      ]) as unknown,
    });
  });
});
