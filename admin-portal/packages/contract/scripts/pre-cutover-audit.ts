/**
 * Read-only audit of production data before a box moves from the Flutter operator app to the
 * native one: lists the `queues` docs that will look or behave differently, and the operator
 * accounts the native app would lock out. It never writes.
 * Run it through `scripts/pre-cutover-audit.sh` at the repo root (see its header), or:
 *
 *   pnpm audit:pre-cutover --project <id>           # a real project, with your credentials
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 pnpm audit:pre-cutover   # the emulator
 *
 * Exit code: 0 nothing to do, 1 something needs attention, 2 usage or runtime error.
 */
import { parseArgs } from 'node:util';
import {
  AUDIT_SECTIONS,
  connect,
  describeTarget,
  resolveTarget,
  runPreCutoverAudit,
} from '../src/admin/index.js';
import { exitCodeOf, renderReport, reportToJson } from '../src/audit/report.js';

const SECTION_IDS = AUDIT_SECTIONS.map((s) => s.id);

const USAGE = `Usage: pre-cutover-audit --project <id> [--section <name>]... [--json]
       FIRESTORE_EMULATOR_HOST=<host:port> pre-cutover-audit [--section <name>]... [--json]

Read-only. Lists the queues that will look or behave differently on the native operator app,
and the operator accounts it would lock out (unverified email, disabled, no usable claims).

  --project <id>    The Firebase project to read. Required unless FIRESTORE_EMULATOR_HOST is set
                    (then it reads the Firestore and Auth emulators).
  --section <name>  Run only this section (${SECTION_IDS.join(', ')}). Repeatable. Default: all.
  --json            Machine-readable output instead of the tables.
  -h, --help        Show this help.

Credentials for a real project: Application Default Credentials
(\`gcloud auth application-default login\`) or a service account key file in
GOOGLE_APPLICATION_CREDENTIALS. Read-only roles are enough: roles/datastore.viewer (queues)
and roles/firebaseauth.viewer (accounts).

Exit code: 0 nothing to do, 1 something needs attention, 2 usage or runtime error.`;

async function main(): Promise<number> {
  const { values } = parseArgs({
    options: {
      project: { type: 'string' },
      section: { type: 'string', multiple: true },
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
  const unknown = (values.section ?? []).filter((id) => !SECTION_IDS.includes(id));
  if (unknown.length > 0) {
    console.error(`Unknown section: ${unknown.join(', ')}. Sections: ${SECTION_IDS.join(', ')}.`);
    return 2;
  }
  const sections = values.section?.length
    ? AUDIT_SECTIONS.filter((s) => values.section?.includes(s.id))
    : AUDIT_SECTIONS;
  // The audit only reads, so the production project is allowed.
  const target = resolveTarget({ project: values.project, allowProduction: true });
  const { db, auth, close } = connect(target);
  try {
    const report = await runPreCutoverAudit({ db, auth }, describeTarget(target), sections);
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
