import type { components } from './generated/schema'

/**
 * The types the screens work with. Everything the OpenAPI document describes comes from the generated schema, so a
 * change in the API breaks the build; the decision object is typed here from Document 3, because the document
 * describes it as a free-form object (it is the engine's own shape, not the API's).
 */
export type PoliciesResponse = components['schemas']['PoliciesResponse']
export type PolicySummary = NonNullable<PoliciesResponse['policies']>[number]
export type PolicyResponse = components['schemas']['PolicyResponse']
export type PolicyParagraph = components['schemas']['Paragraph']
export type RulesetsResponse = components['schemas']['RulesetsResponse']
export type RulesetSummary = components['schemas']['Ruleset']
export type VersionResponse = components['schemas']['VersionResponse']
export type Finding = components['schemas']['FindingResponse']
export type Aggregates = components['schemas']['AggregatesResponse']
export type ErrorEnvelope = components['schemas']['ErrorEnvelope']
export type ErrorDetail = components['schemas']['ErrorDetail']
export type ChatSessionResponse = components['schemas']['ChatSessionResponse']
export type Review = components['schemas']['ReviewResponse']
export type ReviewFinding = components['schemas']['ReviewFindingResponse']
export type FindingKind = ReviewFinding['kind']
export type Explanation = components['schemas']['ExplanationResponse']
export type Audience = Explanation['audience']
/** How a gap is resolved (Document 3, Publishing gate). */
export type GapResolution = NonNullable<
  components['schemas']['AcknowledgementResponse']['resolution']
>

/** The outcomes of the engine (Document 3, Actions and Rules). */
export type Outcome = 'approve' | 'reject' | 'refer'

/** One comparison a rule made, as the trace records it (Document 3, TraceStep object). */
export interface TraceComparison {
  field: string
  op: string
  expected?: unknown
  actual: unknown
  result: boolean
}

/** One action a fired rule applied. */
export interface TraceAction {
  type: 'set' | 'flag' | 'decide'
  field?: string
  from?: unknown
  to?: unknown
  outcome?: Outcome
  terminal?: boolean
  code?: string
}

/** One step of the trace: one rule, whether it fired, what it compared and what it did. */
export interface TraceStep {
  ruleId: string
  label: string
  priority: number
  status: 'fired' | 'not_fired' | 'skipped' | 'disabled' | 'error'
  comparisons?: TraceComparison[]
  actions?: TraceAction[]
  error?: { code: string; detail: string }
  provenance?: Provenance
}

/** A decision as the engine emits it, with the four things the API adds (Document 3, Decision object). */
export interface Decision {
  status: 'OK' | 'ERROR'
  outcome?: Outcome
  reason?: string
  decidingRuleId?: string | null
  terminal?: boolean
  derived: Record<string, number | string | boolean | null>
  flags: { code: string; message: string; ruleId: string }[]
  candidates: { outcome: Outcome; ruleId: string }[]
  trace: TraceStep[]
  errorCode?: string
  errorRuleId?: string
  simulation?: true
  basedOnDecisionId?: string
  overrides?: Record<string, unknown>
  id: string
  caseNo?: number
  rulesetVersion: { id: string; versionNo: number; versionId: string }
  decidedAt: string
  durationMicros: number
}

/** One line of a batch (Document 2, decide: aggregates plus a summary per case). */
export interface CaseResult {
  id: string
  caseNo?: number
  status: 'OK' | 'ERROR'
  outcome?: Outcome
  decidingRuleId?: string
  flags: string[]
}

export interface BatchResult {
  aggregates: Aggregates
  results: CaseResult[]
}

/** Where a rule comes from (Document 3, Provenance). */
export type Provenance =
  | { kind: 'quoted'; paragraph: number; quote: string; confidence?: number }
  | { kind: 'analyst'; note: string; actor: string; changeRequestId?: string }
  | { kind: 'pending'; changeRequestId: string; rationale: string }

/** One rule of the DSL document (Document 3, Actions and Rules); conditions stay unknown until the cell grammar reads them. */
export interface Rule {
  id: string
  label: string
  priority: number
  enabled?: boolean
  condition: unknown
  actions: RuleAction[]
  provenance: Provenance
  tags?: string[]
}

export interface RuleAction {
  type: 'decide' | 'set' | 'flag'
  outcome?: Outcome
  terminal?: boolean
  reason?: string
  field?: string
  value?: unknown
  code?: string
  message?: string
}

/** A field of the case schema (Document 3, Field Schema). */
export interface FieldSchema {
  name: string
  type: 'number' | 'integer' | 'boolean' | 'enum' | 'string' | 'date'
  unit?: string
  values?: string[]
  required?: boolean
  derived?: boolean
  default?: unknown
  minimum?: number
  maximum?: number
  exclusiveMinimum?: number
  exclusiveMaximum?: number
  description?: string
}

/** The whole DSL document of a version (Document 3, Document Structure). */
export interface RuleSetDocument {
  dslVersion: string
  id: string
  name: string
  language: 'he' | 'en'
  description?: string
  fields: FieldSchema[]
  defaults: { outcome: Outcome; reason: string }
  rules: Rule[]
}
