/**
 * Create (or update) an operator or manager account that firestore.rules v1 accepts: an
 * email/password user with `emailVerified: true` and the custom claims `{role, org, sites}`,
 * mirrored into `users/{uid}`. See docs/operator-accounts.md.
 *
 *   pnpm grant-operator --email porto@example.com --org <orgId> --site <siteId>          # emulator
 *   pnpm grant-operator --email porto@example.com --org <orgId> --site <siteId> --project <id>
 *   pnpm grant-operator ... --project santos-1b6f6 --production
 *
 * Credentials for a real project: the service account JSON in FIREBASE_SERVICE_ACCOUNT,
 * or a key file in GOOGLE_APPLICATION_CREDENTIALS. Without --project it targets the Auth
 * and Firestore emulators (FIREBASE_AUTH_EMULATOR_HOST, FIRESTORE_EMULATOR_HOST).
 */
import { createInterface } from 'node:readline';
import { parseArgs } from 'node:util';
import {
  GrantError,
  OPERATOR_ROLES,
  type OperatorRole,
  connect,
  describeTarget,
  grantOperator,
  resolveTarget,
} from '../src/admin/index.js';

const USAGE = `Usage:
  grant-operator --email <email> --org <orgId> --site <siteId> [--site <siteId> ...]
                 [--role operator|manager] [--name <display name>] [--prompt-password]
                 [--dry-run] [--project <id> [--production]] [--by <who>]

  --site           repeatable, or comma-separated; every site must belong to --org
  --role           operator (default) or manager
  --prompt-password  ask for the new account's password instead of generating one
                   (read from stdin when it is not a terminal)
  --dry-run        validate and report, write nothing`;

/** Read a line without echoing it on a terminal; read the first line of stdin otherwise. */
function readSecret(prompt: string): Promise<string> {
  const { stdin, stdout } = process;
  if (!stdin.isTTY) {
    return new Promise((resolve, reject) => {
      const rl = createInterface({ input: stdin });
      let answered = false;
      rl.once('line', (line) => {
        answered = true;
        rl.close();
        resolve(line);
      });
      rl.once('close', () => {
        if (!answered) reject(new GrantError('bad_input', 'no password on stdin'));
      });
    });
  }
  stdout.write(prompt);
  stdin.setRawMode(true);
  stdin.resume();
  stdin.setEncoding('utf8');
  return new Promise((resolve, reject) => {
    let value = '';
    const finish = () => {
      stdin.off('data', onData);
      stdin.setRawMode(false);
      stdin.pause();
      stdout.write('\n');
    };
    const onData = (chunk: string) => {
      for (const ch of chunk) {
        if (ch === '\r' || ch === '\n' || ch === '\u0004') {
          finish();
          resolve(value);
          return;
        }
        if (ch === '\u0003') {
          finish();
          reject(new GrantError('bad_input', 'cancelled'));
          return;
        }
        if (ch === '\u007f' || ch === '\b') value = value.slice(0, -1);
        else value += ch;
      }
    };
    stdin.on('data', onData);
  });
}

async function promptPassword(): Promise<string> {
  const first = await readSecret('Password for the new account: ');
  if (!process.stdin.isTTY) return first;
  const second = await readSecret('Again: ');
  if (first !== second) throw new GrantError('bad_input', 'the passwords do not match');
  return first;
}

async function main(): Promise<number> {
  const { values } = parseArgs({
    options: {
      email: { type: 'string' },
      org: { type: 'string' },
      site: { type: 'string', multiple: true, default: [] },
      role: { type: 'string', default: 'operator' },
      name: { type: 'string' },
      'prompt-password': { type: 'boolean', default: false },
      'dry-run': { type: 'boolean', default: false },
      project: { type: 'string' },
      production: { type: 'boolean', default: false },
      by: { type: 'string', default: 'script:grant-operator' },
      help: { type: 'boolean', short: 'h' },
    },
  });
  if (values.help) {
    console.log(USAGE);
    return 0;
  }
  const sites = values.site.flatMap((s) => s.split(',')).map((s) => s.trim());
  if (values.email === undefined || values.org === undefined || sites.length === 0) {
    console.error(USAGE);
    return 2;
  }
  if (!(OPERATOR_ROLES as readonly string[]).includes(values.role)) {
    console.error(`--role must be one of ${OPERATOR_ROLES.join(', ')}\n\n${USAGE}`);
    return 2;
  }
  const role = values.role as OperatorRole;
  const dryRun = values['dry-run'];

  const target = resolveTarget({ project: values.project, allowProduction: values.production });
  console.log(
    `Target: ${
      target.kind === 'emulator'
        ? `Auth + Firestore emulators (project ${target.projectId})`
        : describeTarget(target)
    }`,
  );
  const { db, auth, close } = connect(target);
  try {
    const r = await grantOperator(auth, db, {
      email: values.email,
      role,
      org: values.org,
      sites,
      displayName: values.name,
      password: values['prompt-password'] ? promptPassword : undefined,
      dryRun,
      by: values.by,
    });
    const scope = `${r.claims.role} of org ${r.claims.org ?? ''}, sites ${r.claims.sites.join(', ')}`;
    if (r.dryRun) {
      console.log(
        `Dry run: would ${r.created ? 'create' : `update ${r.uid ?? ''} (was ${r.previousRole ?? 'no role'})`} ` +
          `${r.email} as ${scope}. Nothing was written.`,
      );
      return 0;
    }
    console.log(
      `${r.created ? 'Created' : 'Updated'} ${r.email} (${r.uid ?? ''}) as ${scope}` +
        `${r.previousRole ? `, was ${r.previousRole}` : ''}.`,
    );
    if (r.generatedPassword !== null) {
      console.log(
        `Password (shown once, hand it over privately): ${r.generatedPassword}\n` +
          'To change it later, reset it in the Firebase console (Authentication > Users).',
      );
    } else if (!r.created) {
      console.log(
        'The password was not changed. A signed-in device picks up the new scope on its next token refresh.',
      );
    }
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
