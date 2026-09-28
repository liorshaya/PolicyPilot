/** The engine's outcomes and error status (Document 3, Decision object). */
export type DecisionStatus = 'approve' | 'reject' | 'refer' | 'error'

/** The words a person reads; the colour never stands alone (the brief: an explicit text label). */
export const DECISION_LABELS: Record<DecisionStatus, string> = {
  approve: 'Approved',
  reject: 'Declined',
  refer: 'Manual review',
  error: 'Evaluation error',
}

/** The words of a rule's action column, where a rule does what the engine will decide (the spec's glossary). */
export const ACTION_LABELS: Record<Exclude<DecisionStatus, 'error'>, string> = {
  approve: 'Approve',
  reject: 'Decline',
  refer: 'Manual review',
}

/** The life of a rule set version (Document 2, Data Model: DRAFT, PUBLISHED, SUPERSEDED). */
export type VersionStatus = 'DRAFT' | 'PUBLISHED' | 'SUPERSEDED'

export const VERSION_LABELS: Record<VersionStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  SUPERSEDED: 'Superseded',
}

/** The words behind a tag, so a test asserts the label a person reads and not a colour. */
export const decisionLabel = (status: DecisionStatus): string => DECISION_LABELS[status]
