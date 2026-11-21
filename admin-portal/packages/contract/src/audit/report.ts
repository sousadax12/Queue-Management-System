/**
 * The shape of a pre-cutover audit report, and how it is printed. A report is a list of
 * sections (`queues`, `accounts`); each section is a table of rows with findings. A new
 * section only has to produce a {@link SectionReport}: the table, the JSON output and the
 * exit code need no change.
 */

/** `info` is shown but needs no action; `attention` makes the audit exit non-zero. */
export type Severity = 'info' | 'attention';

export interface Finding {
  /** Stable machine-readable id, e.g. `video-http`. */
  code: string;
  severity: Severity;
  message: string;
}

export interface AuditRow {
  /** What the row is about, e.g. a queue id. */
  id: string;
  /** Table cells, keyed by {@link AuditColumn.key}. */
  cells: Record<string, string>;
  findings: Finding[];
  /** Section-specific facts for `--json`. */
  details?: Record<string, unknown>;
}

export interface AuditColumn {
  key: string;
  header: string;
}

export interface SectionReport {
  id: string;
  title: string;
  columns: AuditColumn[];
  rows: AuditRow[];
  /** Lines printed under the section header, e.g. what was counted but not listed. */
  notes?: string[];
  /** Section-wide counts for `--json`. */
  summary?: Record<string, number>;
}

export interface AuditReport {
  /** Where the data came from, e.g. `REAL project my-project`. */
  target: string;
  sections: SectionReport[];
}

export const needsAttention = (row: AuditRow): boolean =>
  row.findings.some((f) => f.severity === 'attention');

export const attentionCount = (report: AuditReport): number =>
  report.sections.reduce((n, s) => n + s.rows.filter(needsAttention).length, 0);

/** 0 = nothing to do, 1 = at least one row needs attention. (2 is a usage or runtime error.) */
export const exitCodeOf = (report: AuditReport): 0 | 1 => (attentionCount(report) > 0 ? 1 : 0);

function renderTable(headers: string[], rows: string[][]): string[] {
  const widths = headers.map((h, i) => Math.max(h.length, ...rows.map((r) => (r[i] ?? '').length)));
  const line = (cells: string[]) =>
    cells
      .map((c, i) => c.padEnd(widths[i] ?? 0))
      .join('  ')
      .trimEnd();
  return [line(headers), line(widths.map((w) => '-'.repeat(w))), ...rows.map(line)];
}

function renderSection(section: SectionReport): string[] {
  const flagged = section.rows.filter(needsAttention);
  const out = [
    `== ${section.title}: ${section.rows.length} checked, ${flagged.length} need attention ==`,
    '',
  ];
  if (section.notes?.length) out.push(...section.notes, '');
  if (section.rows.length === 0) return [...out, '(nothing to list)', ''];
  out.push(
    ...renderTable(
      ['STATUS', 'ID', ...section.columns.map((c) => c.header)],
      section.rows.map((r) => [
        needsAttention(r) ? 'ATTENTION' : 'ok',
        r.id,
        ...section.columns.map((c) => r.cells[c.key] ?? ''),
      ]),
    ),
    '',
  );
  const withFindings = section.rows.filter((r) => r.findings.length > 0);
  if (withFindings.length > 0) {
    out.push('Details ([!] needs attention, [i] for information):', '');
    for (const r of withFindings) {
      out.push(r.id);
      for (const f of r.findings) {
        out.push(`  ${f.severity === 'attention' ? '[!]' : '[i]'} ${f.message}`);
      }
      out.push('');
    }
  }
  return out;
}

/** The human-readable report: one table per section, then each row's findings. */
export function renderReport(report: AuditReport): string {
  const n = attentionCount(report);
  return [
    `Pre-cutover audit of ${report.target} (read-only)`,
    '',
    ...report.sections.flatMap(renderSection),
    n === 0
      ? 'Result: nothing needs attention.'
      : `Result: ${n} ${n === 1 ? 'item needs' : 'items need'} attention before the cutover.`,
  ].join('\n');
}

/** The `--json` output. */
export function reportToJson(report: AuditReport): unknown {
  return {
    target: report.target,
    attention: attentionCount(report),
    sections: report.sections.map((s) => ({
      id: s.id,
      title: s.title,
      attention: s.rows.filter(needsAttention).length,
      ...(s.summary ? { summary: s.summary } : {}),
      rows: s.rows.map((r) => ({
        id: r.id,
        status: needsAttention(r) ? 'attention' : 'ok',
        ...r.details,
        findings: r.findings,
      })),
    })),
  };
}
