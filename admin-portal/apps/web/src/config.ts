import { z } from 'zod';

/**
 * Runtime config. Values come from `window.__QMS_ENV__` (written by the container from its
 * env at start, see docker/40-qms-env.sh) and fall back to the `VITE_*` vars Vite inlines
 * at build time (`.env.local` in development). One image can so serve any project and domain.
 */
declare global {
  interface Window {
    __QMS_ENV__?: Record<string, string | undefined>;
  }
}

const optional = z.string().min(1).optional();

export const configSchema = z.object({
  VITE_FIREBASE_API_KEY: z.string().min(1),
  VITE_FIREBASE_AUTH_DOMAIN: z.string().min(1),
  VITE_FIREBASE_PROJECT_ID: z.string().min(1),
  VITE_FIREBASE_APP_ID: z.string().min(1),
  VITE_FIREBASE_STORAGE_BUCKET: optional,
  VITE_FIREBASE_MESSAGING_SENDER_ID: optional,
  /** Public origin of the portal, e.g. https://admin.example.com. Defaults to the page origin. */
  VITE_PUBLIC_BASE_URL: z.url({ protocol: /^https?$/ }).optional(),
  /** e.g. http://127.0.0.1:9099. Connects Firebase Auth to the emulator. Never set in production. */
  VITE_FIREBASE_AUTH_EMULATOR_URL: z.url({ protocol: /^https?$/ }).optional(),
});

export interface AppConfig {
  firebase: {
    apiKey: string;
    authDomain: string;
    projectId: string;
    appId: string;
    storageBucket?: string;
    messagingSenderId?: string;
  };
  /** No trailing slash. */
  baseUrl: string;
  authEmulatorUrl?: string;
}

export type ConfigResult = { ok: true; config: AppConfig } | { ok: false; missing: string[] };

/** Merge the sources (runtime wins), drop empty strings and validate. */
export function readConfig(
  sources: readonly (Record<string, unknown> | undefined)[],
  fallbackOrigin: string,
): ConfigResult {
  const merged: Record<string, unknown> = {};
  for (const source of [...sources].reverse()) {
    for (const [k, v] of Object.entries(source ?? {})) {
      if (typeof v === 'string' && v.trim() !== '') merged[k] = v.trim();
    }
  }
  const parsed = configSchema.safeParse(merged);
  if (!parsed.success) {
    return { ok: false, missing: [...new Set(parsed.error.issues.map((i) => i.path.join('.')))] };
  }
  const env = parsed.data;
  return {
    ok: true,
    config: {
      firebase: {
        apiKey: env.VITE_FIREBASE_API_KEY,
        authDomain: env.VITE_FIREBASE_AUTH_DOMAIN,
        projectId: env.VITE_FIREBASE_PROJECT_ID,
        appId: env.VITE_FIREBASE_APP_ID,
        ...(env.VITE_FIREBASE_STORAGE_BUCKET && {
          storageBucket: env.VITE_FIREBASE_STORAGE_BUCKET,
        }),
        ...(env.VITE_FIREBASE_MESSAGING_SENDER_ID && {
          messagingSenderId: env.VITE_FIREBASE_MESSAGING_SENDER_ID,
        }),
      },
      baseUrl: (env.VITE_PUBLIC_BASE_URL ?? fallbackOrigin).replace(/\/+$/, ''),
      ...(env.VITE_FIREBASE_AUTH_EMULATOR_URL && {
        authEmulatorUrl: env.VITE_FIREBASE_AUTH_EMULATOR_URL,
      }),
    },
  };
}

export function readBrowserConfig(): ConfigResult {
  return readConfig([window.__QMS_ENV__, import.meta.env], window.location.origin);
}
