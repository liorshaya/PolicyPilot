import type { Finding, Review, ReviewFinding } from '../../api/types'
import { VERSION_LABELS, type VersionStatus } from '../../shared/ui/decisionLabels'
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

/** The shape of a finding's mark, by what the publish gate does with it (the spec, section 06). */
export type MarkShape = 'square' | 'bar' | 'triangle'

const SHAPES: Record<FindingMark, MarkShape> = {
  error: 'square',
  injection: 'bar',
  warning: 'triangle',
}

/** A square must be acknowledged before publishing, a bar is an instruction planted in the text, a triangle may stay. */
export function markOf(finding: ReviewFinding): MarkShape {
  return SHAPES[KIND_MARKS[finding.kind]]
}

/**
 * The review's head (the spec, section 09: "Review · 10 findings · 7 block publishing · 1 acknowledged"): every finding,
 * those that block publishing now, as the API marks them, and those a person acknowledged.
 */
export function reviewCounts(review: Review): {
  findings: number
  blocking: number
  acknowledged: number
} {
  return {
    findings: review.findings.length,
    blocking: review.findings.filter((finding) => finding.blocking).length,
    acknowledged: review.findings.filter((finding) => finding.acknowledgement !== undefined).length,
  }
}

/** The paragraphs a review was read against, from its coverage: "¶ 1–9", or null when it names none. */
function coveredParagraphs(review: Review): string | null {
  const indexes = Object.keys(review.coverage)
    .map(Number)
    .filter((index) => Number.isInteger(index))
  if (indexes.length === 0) {
    return null
  }
  const first = Math.min(...indexes)
  const last = Math.max(...indexes)
  return first === last ? `¶ ${String(first)}` : `¶ ${String(first)}–${String(last)}`
}

/**
 * The review's lifecycle in the product's own sentences (the spec, section 09). A review carries no time, so a done one
 * says what it was read against and not when (the owner's answer of 2026-09-28 to phase 3's third question).
 */
export function reviewStatusLine(review: Review | undefined): string {
  if (review === undefined) {
    return 'Not reviewed yet.'
  }
  if (review.status === 'FAILED') {
    return 'The review could not run.'
  }
  if (review.status === 'STALE') {
    return 'The draft was edited after its review; run the review again.'
  }
  return `Reviewed against ${coveredParagraphs(review) ?? 'the policy'}. The draft has not changed since.`
}

/** One gate of the publish box: what must hold, whether it holds, and the fact that says so. */
export interface PublishGate {
  label: string
  state: 'ok' | 'fail' | 'wait'
  fact: string
}

/** A finding that blocked publishing: one that blocks now, or one of the kinds that must be acknowledged, acknowledged. */
const blocked = (finding: ReviewFinding) =>
  finding.blocking || (finding.acknowledgement !== undefined && markOf(finding) !== 'triangle')

/**
 * The real gates of publishing (the spec, section 09, the publish box; Document 2, Flow 1), each with its fact: only a
 * draft is published; the validator found no error; the draft was reviewed and not edited since; every blocking finding
 * was acknowledged, n of m.
 */
export function publishGates(
  version: { status: string; versionNo: number },
  review: Review | undefined,
  findings: Finding[],
): PublishGate[] {
  const status = version.status as VersionStatus
  const problems = findings.filter((finding) => finding.severity === 'error').length
  const blocking = (review?.findings ?? []).filter(blocked)
  const acknowledged = blocking.filter((finding) => !finding.blocking).length
  return [
    {
      label: 'Only a draft is published',
      state: status === 'DRAFT' ? 'ok' : 'fail',
      fact: `${VERSION_LABELS[status]} v${String(version.versionNo)}`,
    },
    {
      label: 'Schema and semantics valid',
      state: problems === 0 ? 'ok' : 'fail',
      fact: `${String(problems)} problem${problems === 1 ? '' : 's'}`,
    },
    {
      label: 'Reviewed, and not edited since',
      ...(review === undefined
        ? { state: 'fail' as const, fact: 'not reviewed' }
        : review.status === 'FAILED'
          ? { state: 'fail' as const, fact: 'could not run' }
          : review.status === 'STALE'
            ? { state: 'wait' as const, fact: 'edited after its review' }
            : { state: 'ok' as const, fact: coveredParagraphs(review) ?? '' }),
    },
    {
      label: 'Blocking findings acknowledged',
      state: acknowledged === blocking.length ? 'ok' : 'fail',
      fact: `${String(acknowledged)} of ${String(blocking.length)}`,
    },
  ]
}
