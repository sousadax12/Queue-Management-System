/**
 * Make an existing Firebase Auth user a platform admin: set the `role` custom claim and
 * mirror it into `users/{uid}`. The user must have signed in to the portal once.
 *
 *   pnpm grant-platform-admin --email you@example.com                        # emulator
 *   pnpm grant-platform-admin --email you@example.com --project <id>         # real project
 *   pnpm grant-platform-admin --uid <uid> --project santos-1b6f6 --production
 *
 * Credentials for a real project: the service account JSON in FIREBASE_SERVICE_ACCOUNT,
 * or a key file in GOOGLE_APPLICATION_CREDENTIALS. Without --project it targets the Auth
 * and Firestore emulators (FIREBASE_AUTH_EMULATOR_HOST, FIRESTORE_EMULATOR_HOST).
 */
import { parseArgs } from 'node:util';
import {
  GrantError,
  connect,
  describeTarget,
  grantPlatformAdmin,
  resolveTarget,
} from '../src/admin/index.js';

const USAGE = `Usage:
  grant-platform-admin (--email <email> | --uid <uid>) [--project <id> [--production]] [--by <who>]`;

async function main(): Promise<number> {
  const { values } = parseArgs({
    options: {
      email: { type: 'string' },
      uid: { type: 'string' },
      project: { type: 'string' },
      production: { type: 'boolean', default: false },
      by: { type: 'string', default: 'script:grant-platform-admin' },
      help: { type: 'boolean', short: 'h' },
    },
  });
  if (values.help) {
    console.log(USAGE);
    return 0;
  }
  if ((values.email === undefined) === (values.uid === undefined)) {
    console.error(USAGE);
    return 2;
  }
  const target = resolveTarget({ project: values.project, allowProduction: values.production });
  console.log(`Target: ${describeTarget(target)}`);
  const { db, auth, close } = connect(target);
  try {
    const r = await grantPlatformAdmin(auth, db, {
      email: values.email,
      uid: values.uid,
      by: values.by,
    });
    console.log(
      `Granted platformAdmin to ${r.email} (${r.uid})${r.previousRole ? `, was ${r.previousRole}` : ''}. ` +
        'They get it on their next token refresh ("Check again" on the no-access page, or sign in again).',
    );
    return 0;
  } finally {
    await close();
  }
}

main().then(
  (code) => {
    process.exitCode = code;
  },
  (err: unknown) => {
    if (err instanceof GrantError) console.error(`Grant failed (${err.code}): ${err.message}`);
    else console.error(err);
    process.exitCode = 1;
  },
);
