import type { Finding, Review, ReviewFinding } from '../../api/types'
import { KIND_LABELS, KIND_MARKS, type FindingMark } from '../../shared/ui/findingKinds'

/**
 * The reviewer's findings as the screens show them (Document 4, Prompt 2: Review; Document 2, Flow 1). The kinds are
 * named the way the analyst reads them, and what blocks a publish is the API's own rule, read from the review: the
 * screen never decides it on its own.
 */

/** What each kind is called: the words the severity mark writes beside itself (Document 4, the kind table). */
export { KIND_LABELS }

/** How a gap can be resolved (Document 3, Publishing gate), in the words of the dialog. */
export const RESOLUTION_LABELS = {
  rule_added: 'A rule now covers the passage',
  flag_added: 'A manual-check flag surfaces it',
  interpretation: 'An existing rule already covers it',
} as const

/** A finding's mark on a row of the decision table, and what it says on hover: "F-1 Conflict". */
export interface TableMark {
  mark: FindingMark
  label: string
}

/** The reviewer's findings still open: an acknowledged one neither blocks nor warns. */
const open = (review: Review | undefined) =>
  (review?.findings ?? []).filter((finding) => finding.acknowledgement === undefined)

/**
 * The marks of the decision table's gutter, by rule (the spec, section 07; Document 3, Layout: "validation and reviewer
 * findings anchored to the rule"): each open finding of the review on every rule it names, marked by its kind, and each
 * finding of the validator, a square for an error and a triangle for a warning.
 */
export function tableMarks(
  review: Review | undefined,
  findings: Finding[],
): Map<string, TableMark[]> {
  const marks = new Map<string, TableMark[]>()
  const put = (ruleIds: string[], mark: TableMark) => {
    for (const ruleId of ruleIds) {
      marks.set(ruleId, [...(marks.get(ruleId) ?? []), mark])
    }
  }
  for (const finding of open(review)) {
    put(finding.ruleIds, {
      mark: KIND_MARKS[finding.kind],
      label: `${finding.id} ${KIND_LABELS[finding.kind]}`,
    })
  }
  for (const finding of findings) {
    put(finding.ruleIds, {
      mark: finding.severity === 'error' ? 'error' : 'warning',
      label: `${finding.code} ${finding.message}`,
    })
  }
  return marks
}

/**
 * The strip's counts above the table: the findings that block publishing, as the API marks them and as every error
 * of the validator does, and the open ones that only warn.
 */
export function tableCounts(
  review: Review | undefined,
  findings: Finding[],
): { block: number; warn: number } {
  const reviewed = open(review)
  const errors = findings.filter((finding) => finding.severity === 'error').length
  return {
    block: reviewed.filter((finding) => finding.blocking).length + errors,
    warn: reviewed.filter((finding) => !finding.blocking).length + findings.length - errors,
  }
}

/** The cells a finding of the validator names, by rule and field: an error underlines over a warning. */
export function underlinedCells(findings: Finding[]): Map<string, Map<string, 'err' | 'warn'>> {
  const cells = new Map<string, Map<string, 'err' | 'warn'>>()
  for (const finding of findings) {
    for (const ruleId of finding.ruleIds) {
      for (const field of finding.fieldNames) {
        const fields = cells.get(ruleId) ?? new Map<string, 'err' | 'warn'>()
        fields.set(
          field,
          finding.severity === 'error' || fields.get(field) === 'err' ? 'err' : 'warn',
        )
        cells.set(ruleId, fields)
      }
    }
  }
  return cells
}

/** Why a draft cannot be published yet, as sentences; empty when its review allows it (Document 2, Flow 1). */
export function publishBlockers(review: Review | undefined): string[] {
  if (review === undefined) {
    return ['The draft has not been reviewed yet.']
  }
  if (review.status === 'FAILED') {
    return ['The review could not run; run it again.']
  }
  if (review.status === 'STALE') {
    return ['The draft was edited after its review; run the review again.']
  }
  const open = review.findings.filter((finding) => finding.blocking)
  return open.length === 0
    ? []
    : [
        `${String(open.length)} finding${open.length === 1 ? '' : 's'} must be acknowledged: ${open
          .map((finding) => finding.id)
          .join(', ')}.`,
      ]
}

/** What an acknowledgement of this finding needs from the analyst (Document 2, the acknowledge route). */
export function needs(finding: ReviewFinding): 'resolution' | 'note' | 'nothing' {
  if (finding.kind === 'gap') {
    return 'resolution'
  }
  return finding.severity === 'error' ? 'note' : 'nothing'
}
