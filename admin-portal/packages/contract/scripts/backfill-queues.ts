/**
 * One-off: set orgId + siteId on existing `queues` docs (C10). Dry run unless --apply.
 *
 *   pnpm backfill:queues --org <orgId> --site <siteId>                      # emulator, dry run
 *   pnpm backfill:queues --org <orgId> --site <siteId> --project <id> --apply
 *
 * Options: --queue <id> (repeatable) limits the run; --allow-missing-site applies even if
 * sites/<siteId> does not exist yet; --by <uid> sets updatedBy. A production project id also
 * needs --production.
 */
import { parseArgs } from 'node:util';
import { backfillQueues, connect, describeTarget, resolveTarget } from '../src/admin/index.js';

const USAGE =
  'Usage: backfill-queues --org <orgId> --site <siteId> [--queue <id>]... [--project <id> [--production]] [--apply] [--allow-missing-site] [--by <uid>]';

async function main(): Promise<number> {
  const { values } = parseArgs({
    options: {
      org: { type: 'string' },
      site: { type: 'string' },
      queue: { type: 'string', multiple: true },
      project: { type: 'string' },
      production: { type: 'boolean', default: false },
      apply: { type: 'boolean', default: false },
      'allow-missing-site': { type: 'boolean', default: false },
      by: { type: 'string', default: 'script:backfill-queues' },
      help: { type: 'boolean', short: 'h' },
    },
  });
  if (values.help || !values.org || !values.site) {
    console.log(USAGE);
    return values.help ? 0 : 2;
  }
  const target = resolveTarget({ project: values.project, allowProduction: values.production });
  console.log(
    `Target: ${describeTarget(target)}${values.apply ? '' : '  [DRY RUN, pass --apply to write]'}`,
  );
  const { db, close } = connect(target);
  try {
    const report = await backfillQueues(db, {
      orgId: values.org,
      siteId: values.site,
      queueIds: values.queue,
      apply: values.apply,
      allowMissingSite: values['allow-missing-site'],
      by: values.by,
    });
    if (!report.siteExists) console.warn(`WARNING: sites/${values.site} does not exist yet.`);
    for (const e of report.entries) {
      const missing = e.missingLegacyFields.length
        ? `  missing legacy fields: ${e.missingLegacyFields.join(', ')}`
        : '';
      console.log(
        `${e.action.padEnd(11)} queues/${e.id} "${e.name ?? ''}" (orgId=${e.orgId ?? '-'}, siteId=${e.siteId ?? '-'})${missing}`,
      );
    }
    const count = (a: string) => report.entries.filter((e) => e.action === a).length;
    console.log(
      `${report.entries.length} queues: ${count('update')} to update, ${count('already-set')} already set, ${count('conflict')} conflicts. ${report.applied ? `Updated ${report.updated}.` : 'Nothing written.'}`,
    );
    return count('conflict') > 0 ? 1 : 0;
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
    process.exitCode = 1;
  },
);
