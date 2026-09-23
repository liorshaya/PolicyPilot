import type { Diff, FieldSchema, Finding, Outcome, Rule } from '../../api/types'

/**
 * The change stream's events as the API sends them (Document 2, POST /rulesets/{id}/versions/{no}/changes; Document 3,
 * Change Patches, Diff and Versioning). SSE payloads are not in the OpenAPI document, so they are typed here from the
 * API's own records (ChangeEventPayloads, StreamFailure, RegressionResponse), as the chat's are.
 */

/** The stages the stream reports, in the order it reports them. */
export const CHANGE_STAGES = ['analyzing', 'proposing', 'validating', 'regression'] as const

export type ChangeStage = (typeof CHANGE_STAGES)[number]

/** `proposing`: the rules the model is shown, in evaluation order, and the fields the request touches. */
export interface Candidates {
  candidates: string[]
  fields: string[]
}

/** One patch of a proposal (Document 3, Patch format); every patch carries its rationale in the policy's language. */
export type Patch =
  | { op: 'add' | 'replace'; ruleId: string; rule: Rule; rationale: string }
  | { op: 'remove'; ruleId: string; rationale: string }
  | { op: 'add_field'; field: FieldSchema; rationale: string }
  | { op: 'set_defaults'; defaults: { outcome: Outcome; reason: string }; rationale: string }

/** An outcome as the regression reads it: the engine's, or `error` when the copy cannot decide the input. */
export type RegressionOutcome = Outcome | 'error'

/** One flipped decision (Document 3, Regression report); `caseNo` is null for a case decided on its own. */
export interface Flip {
  decisionId: string
  caseNo: number | null
  before: RegressionOutcome
  after: RegressionOutcome
  decidingRuleBefore: string | null
  decidingRuleAfter: string | null
}

/**
 * The regression report: how many of the sandbox's decisions on the base version the copy decided again, every
 * flipped outcome by case number, and the count of each transition, keyed `approve → reject`.
 */
export interface Regression {
  decisions: number
  flips: Flip[]
  transitions: Record<string, number>
}

/** `proposal`: the stored PROPOSED change request, with its diff and its regression report. */
export interface Proposal {
  id: string
  status: 'PROPOSED'
  baseVersionId: string
  summary: string
  patches: Patch[]
  /** The candidates the model considered and left unchanged. */
  untouched: string[]
  notes: string
  candidates: string[]
  fields: string[]
  diff: Diff
  regression: Regression
  createdAt: string
}

/**
 * `error`: the code, the findings, and the model's last answer when there was one. A proposal the validator refused
 * (Document 5, RT-04) ends here with RULESET_INVALID, and nothing of it was stored.
 */
export interface StreamFailure {
  code: string
  findings: Finding[]
  document: unknown
}
