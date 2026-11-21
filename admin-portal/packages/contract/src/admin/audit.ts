import type { Auth } from 'firebase-admin/auth';
import type { Firestore } from 'firebase-admin/firestore';
import { QUEUE_COLUMNS, auditQueue } from '../audit/queues.js';
import type { AuditReport, SectionReport } from '../audit/report.js';

/** Read handles for the audit. Sections must only read: no set, update, delete or batch. */
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

export const AUDIT_SECTIONS: readonly AuditSection[] = [queuesSection];

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
