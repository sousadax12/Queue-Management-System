/**
 * Read-only audit of production data before a box moves from the Flutter operator app to the
 * native one: lists the `queues` docs that will look or behave differently. It never writes.
 * Run it through `scripts/pre-cutover-audit.sh` at the repo root (see its header), or:
 *
 *   pnpm audit:pre-cutover --project <id>           # a real project, with your credentials
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 pnpm audit:pre-cutover   # the emulator
 *
 * Exit code: 0 nothing to do, 1 something needs attention, 2 usage or runtime error.
 */
import { parseArgs } from 'node:util';
import { connect, describeTarget, resolveTarget, runPreCutoverAudit } from '../src/admin/index.js';
import { exitCodeOf, renderReport, reportToJson } from '../src/audit/report.js';

const USAGE = `Usage: pre-cutover-audit --project <id> [--json]
       FIRESTORE_EMULATOR_HOST=<host:port> pre-cutover-audit [--json]

Read-only. Lists the queues that will look or behave differently on the native operator app.

  --project <id>  The Firebase project to read. Required unless FIRESTORE_EMULATOR_HOST is set.
  --json          Machine-readable output instead of the table.
  -h, --help      Show this help.

Credentials for a real project: Application Default Credentials
(\`gcloud auth application-default login\`) or a service account key file in
GOOGLE_APPLICATION_CREDENTIALS. A read-only role such as roles/datastore.viewer is enough.

Exit code: 0 nothing to do, 1 something needs attention, 2 usage or runtime error.`;

async function main(): Promise<number> {
  const { values } = parseArgs({
    options: {
      project: { type: 'string' },
      json: { type: 'boolean', default: false },
      help: { type: 'boolean', short: 'h' },
    },
  });
  if (values.help) {
    console.log(USAGE);
    return 0;
  }
  if (values.project === undefined && !process.env.FIRESTORE_EMULATOR_HOST) {
    console.error(
      `Pass --project <id> (or set FIRESTORE_EMULATOR_HOST for the emulator).\n\n${USAGE}`,
    );
    return 2;
  }
  // The audit only reads, so the production project is allowed.
  const target = resolveTarget({ project: values.project, allowProduction: true });
  const { db, auth, close } = connect(target);
  try {
    const report = await runPreCutoverAudit({ db, auth }, describeTarget(target));
    console.log(values.json ? JSON.stringify(reportToJson(report), null, 2) : renderReport(report));
    return exitCodeOf(report);
  } finally {
    await close();
  }
}

main().then(
  (code) => {
    process.exitCode = code;
  },
  (err: unknown) => {
    console.error(err instanceof Error ? err.message : err);
    process.exitCode = 2;
  },
);
