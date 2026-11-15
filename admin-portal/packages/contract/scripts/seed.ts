/**
 * Seed the contract fixtures, create pairing codes and claim devices.
 *
 *   pnpm seed                                        # every fixture -> Firestore emulator
 *   pnpm seed claim <code> <siteId> <screenId> [--name "Box 3"] [--by <uid>]
 *   pnpm seed pairing-code [--uid <deviceUid>] [--code <CODE>]
 *
 * Targets the emulator (FIRESTORE_EMULATOR_HOST, default 127.0.0.1:8080, project demo-qms)
 * unless --dev-project <id> is given. The production project is always refused.
 */
import { parseArgs } from 'node:util';
import { ClaimError } from '../src/claim.js';
import {
  claimWithAdmin,
  connect,
  createPairingCode,
  describeTarget,
  resolveTarget,
  writeDocs,
} from '../src/admin/index.js';
import { loadContractFixtures } from '../src/fixtures.js';

const USAGE = `Usage:
  seed [--dev-project <id>]
  seed claim <code> <siteId> <screenId> [--name <name>] [--by <uid>] [--dev-project <id>]
  seed pairing-code [--uid <deviceUid>] [--code <CODE>] [--dev-project <id>]`;

const UNCLAIMED_FIXTURE_UID = 'tvUnclaimed01uidCCCCCCCCCC';

async function main(): Promise<number> {
  const { values, positionals } = parseArgs({
    allowPositionals: true,
    options: {
      'dev-project': { type: 'string' },
      name: { type: 'string' },
      by: { type: 'string', default: 'script:seed' },
      uid: { type: 'string', default: UNCLAIMED_FIXTURE_UID },
      code: { type: 'string' },
      help: { type: 'boolean', short: 'h' },
    },
  });
  if (values.help) {
    console.log(USAGE);
    return 0;
  }
  const [command = 'seed', ...args] = positionals;
  const target = resolveTarget({ project: values['dev-project'] });
  console.log(`Target: ${describeTarget(target)}`);
  const { db, close } = connect(target);
  try {
    switch (command) {
      case 'seed': {
        const result = await writeDocs(db, loadContractFixtures());
        console.log(`Wrote ${result.written} docs:`, result.byCollection);
        console.log(`Claim the unclaimed box with: pnpm seed claim K7M3QX <siteId> <screenId>`);
        return 0;
      }
      case 'claim': {
        const [code, siteId, screenId] = args;
        if (!code || !siteId || !screenId || args.length !== 3) {
          console.error(USAGE);
          return 2;
        }
        const r = await claimWithAdmin(db, {
          code,
          siteId,
          screenId,
          name: values.name,
          by: values.by,
        });
        console.log(
          `Claimed ${r.uid} as "${r.name}" -> org ${r.orgId}, site ${r.siteId}, screen ${r.screenId}${r.moved ? ' (moved)' : ''}`,
        );
        return 0;
      }
      case 'pairing-code': {
        const code = await createPairingCode(db, values.uid, values.code);
        console.log(`Created pairing code ${code} for device ${values.uid} (valid 15 min)`);
        return 0;
      }
      default:
        console.error(`Unknown command "${command}"\n${USAGE}`);
        return 2;
    }
  } finally {
    await close();
  }
}

main().then(
  (code) => {
    process.exitCode = code;
  },
  (err: unknown) => {
    if (err instanceof ClaimError) console.error(`Claim failed (${err.code}): ${err.message}`);
    else console.error(err);
    process.exitCode = 1;
  },
);
