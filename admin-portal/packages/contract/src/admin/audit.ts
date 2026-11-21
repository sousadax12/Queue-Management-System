import type { Auth } from 'firebase-admin/auth';
import type { Firestore } from 'firebase-admin/firestore';
import { ACCOUNT_COLUMNS, auditAccount, hasOperatorClaim } from '../audit/accounts.js';
import { QUEUE_COLUMNS, auditQueue } from '../audit/queues.js';
import type { AuditReport, SectionReport } from '../audit/report.js';

/**
 * Read handles for the audit. Sections must only read: no set, update, delete or batch, and no Auth
 * create, update, delete or claim call.
 */
export interface AuditContext {
  db: Firestore;
  auth: Auth;
}

/** One part of the pre-cutover audit. Add a section here to add it to the report. */
export interface AuditSection {
  id: string;
  run(ctx: AuditContext): Promise<SectionReport>;
}

/** Every `queues` doc, checked for what changes on the native operator app. One read. */
export const queuesSection: AuditSection = {
  id: 'queues',
  async run({ db }) {
    const snap = await db.collection('queues').get();
    const rows = snap.docs
      .map((d) => auditQueue(d.id, d.data()))
      .sort((a, b) => a.id.localeCompare(b.id));
    return { id: 'queues', title: 'Queues', columns: QUEUE_COLUMNS, rows };
  },
};

/**
 * Every Firebase Auth account with an operator claim, checked for whether the native operator
 * app would let it in. Accounts without such a claim are only counted. Reads with `listUsers`.
 */
export const accountsSection: AuditSection = {
  id: 'accounts',
  async run({ auth }) {
    const rows = [];
    let total = 0;
    let pageToken: string | undefined;
    do {
      const page = await auth.listUsers(1000, pageToken);
      total += page.users.length;
      for (const user of page.users) {
        if (hasOperatorClaim(user.customClaims)) rows.push(auditAccount(user));
      }
      pageToken = page.pageToken;
    } while (pageToken);
    rows.sort((a, b) => a.id.localeCompare(b.id));
    const others = total - rows.length;
    return {
      id: 'accounts',
      title: 'Operator accounts',
      columns: ACCOUNT_COLUMNS,
      rows,
      notes: [
        `${total} ${total === 1 ? 'account' : 'accounts'} in Firebase Auth: ${rows.length} with an operator claim (listed), ${others} without (not listed).`,
      ],
      summary: { accounts: total, withOperatorClaim: rows.length, withoutOperatorClaim: others },
    };
  },
};

export const AUDIT_SECTIONS: readonly AuditSection[] = [queuesSection, accountsSection];

/** Runs the sections in order against the target. Read-only. */
export async function runPreCutoverAudit(
  ctx: AuditContext,
  target: string,
  sections: readonly AuditSection[] = AUDIT_SECTIONS,
): Promise<AuditReport> {
  const reports: SectionReport[] = [];
  for (const section of sections) reports.push(await section.run(ctx));
  return { target, sections: reports };
}
