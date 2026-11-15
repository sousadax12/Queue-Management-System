import { type App, deleteApp, getApps, initializeApp } from 'firebase-admin/app';
import { type Firestore, getFirestore } from 'firebase-admin/firestore';

/** `demo-` project ids never reach production; firebase-tools runs them emulator-only. */
export const DEMO_PROJECT_ID = 'demo-qms';
export const DEFAULT_EMULATOR_HOST = '127.0.0.1:8080';
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
  if (env.FIRESTORE_EMULATOR_HOST) {
    throw new Error(
      `FIRESTORE_EMULATOR_HOST is set (${env.FIRESTORE_EMULATOR_HOST}) but a real project (${opts.project}) was requested; unset one of them`,
    );
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

/** A Firestore handle for the target, on its own named Admin app. */
export function connect(target: Target): { db: Firestore; close: () => Promise<void> } {
  if (target.kind === 'emulator') {
    process.env.FIRESTORE_EMULATOR_HOST = target.host;
    // No credentials are needed; stop google-auth from probing the GCE metadata server.
    process.env.METADATA_SERVER_DETECTION ??= 'none';
  }
  const app: App = initializeApp({ projectId: target.projectId }, `qms-contract-${++counter}`);
  return { db: getFirestore(app), close: () => deleteApp(app) };
}

export async function closeAll(): Promise<void> {
  await Promise.all(getApps().map((a) => deleteApp(a)));
}
