import { type App, cert, deleteApp, getApps, initializeApp } from 'firebase-admin/app';
import { type Auth, getAuth } from 'firebase-admin/auth';
import { type Firestore, getFirestore } from 'firebase-admin/firestore';

/** `demo-` project ids never reach production; firebase-tools runs them emulator-only. */
export const DEMO_PROJECT_ID = 'demo-qms';
export const DEFAULT_EMULATOR_HOST = '127.0.0.1:8080';
export const DEFAULT_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
/** The Flutter app's live project (lib/firebase_options.dart). */
export const PRODUCTION_PROJECT_IDS: readonly string[] = ['santos-1b6f6'];

export type Target =
  { kind: 'emulator'; projectId: string; host: string } | { kind: 'project'; projectId: string };

export interface TargetOptions {
  /** A real project id. Omitted = the Firestore emulator. */
  project?: string | undefined;
  /** Allow a production project id (backfill only; the seed never sets this). */
  allowProduction?: boolean;
  env?: NodeJS.ProcessEnv;
}

/**
 * The emulator is the default. A real project needs an explicit id, and a production
 * project needs `allowProduction` on top of that.
 */
export function resolveTarget(opts: TargetOptions = {}): Target {
  const env = opts.env ?? process.env;
  if (opts.project === undefined) {
    return {
      kind: 'emulator',
      projectId: env.GCLOUD_PROJECT ?? DEMO_PROJECT_ID,
      host: env.FIRESTORE_EMULATOR_HOST ?? DEFAULT_EMULATOR_HOST,
    };
  }
  for (const name of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST'] as const) {
    if (env[name]) {
      throw new Error(
        `${name} is set (${env[name]}) but a real project (${opts.project}) was requested; unset one of them`,
      );
    }
  }
  if (opts.project.startsWith('demo-')) {
    throw new Error(`${opts.project} is a demo project id; it only exists in the emulator`);
  }
  if (PRODUCTION_PROJECT_IDS.includes(opts.project) && !opts.allowProduction) {
    throw new Error(`${opts.project} is the production project; refusing to write to it`);
  }
  return { kind: 'project', projectId: opts.project };
}

export function describeTarget(t: Target): string {
  return t.kind === 'emulator'
    ? `Firestore emulator ${t.host} (project ${t.projectId})`
    : `REAL project ${t.projectId}`;
}

let counter = 0;

/**
 * Firestore and Auth handles for the target, on their own named Admin app.
 *
 * A real project uses the service account JSON in `FIREBASE_SERVICE_ACCOUNT` if set,
 * else Application Default Credentials (`GOOGLE_APPLICATION_CREDENTIALS`).
 */
export function connect(
  target: Target,
  env: NodeJS.ProcessEnv = process.env,
): { db: Firestore; auth: Auth; close: () => Promise<void> } {
  let credential;
  if (target.kind === 'emulator') {
    process.env.FIRESTORE_EMULATOR_HOST = target.host;
    process.env.FIREBASE_AUTH_EMULATOR_HOST ??= DEFAULT_AUTH_EMULATOR_HOST;
    // No credentials are needed; stop google-auth from probing the GCE metadata server.
    process.env.METADATA_SERVER_DETECTION ??= 'none';
  } else if (env.FIREBASE_SERVICE_ACCOUNT) {
    credential = cert(JSON.parse(env.FIREBASE_SERVICE_ACCOUNT) as object);
  }
  const app: App = initializeApp(
    { projectId: target.projectId, ...(credential ? { credential } : {}) },
    `qms-contract-${++counter}`,
  );
  return { db: getFirestore(app), auth: getAuth(app), close: () => deleteApp(app) };
}

export async function closeAll(): Promise<void> {
  await Promise.all(getApps().map((a) => deleteApp(a)));
}
