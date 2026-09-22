import type { FindingKind, Review, ReviewFinding } from '../../api/types'

/**
 * The reviewer's findings as the screens show them (Document 4, Prompt 2: Review; Document 2, Flow 1). The kinds are
 * named the way the analyst reads them, and what blocks a publish is the API's own rule, read from the review: the
 * screen never decides it on its own.
 */

/** What each kind means to the analyst (Document 4, the kind table). */
export const KIND_LABELS: Record<FindingKind, string> = {
  ambiguity: 'Ambiguity',
  conflict: 'Conflict',
  unsupported: 'Unsupported',
  gap: 'Gap',
  duplicate: 'Duplicate',
  injection: 'Instruction in the text',
}

/** How a gap can be resolved (Document 3, Publishing gate), in the words of the dialog. */
export const RESOLUTION_LABELS = {
  rule_added: 'A rule now covers the passage',
  flag_added: 'A manual-check flag surfaces it',
  interpretation: 'An existing rule already covers it',
} as const

/** The findings that name each rule, so a row of the decision table can carry them. */
export function findingsByRule(review: Review | undefined): Map<string, ReviewFinding[]> {
  const byRule = new Map<string, ReviewFinding[]>()
  for (const finding of review?.findings ?? []) {
    for (const ruleId of finding.ruleIds) {
      byRule.set(ruleId, [...(byRule.get(ruleId) ?? []), finding])
    }
  }
  return byRule
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
