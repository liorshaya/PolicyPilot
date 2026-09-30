import type { EngineDecision, TraceAction, TraceComparison, TraceStep } from '../../api/types'
import { ACTION_LABELS } from '../../shared/ui/decisionLabels'
import { literalText, renderCell, type Leaf } from '../rules/cellGrammar'
import { bandOf } from '../rules/tableModel'

/**
 * The trace as a person reads it (Document 3, Trace format): the steps the engine walked, in the order it walked
 * them, with what each one compared and what it concluded. Nothing here decides anything or computes a value the
 * engine did not emit: it names and groups what the engine already wrote (Document 9, phase 3: "trace.ts only groups").
 */

/** The words for a step's status, so a colour never stands alone. */
export const STEP_LABELS: Record<TraceStep['status'], string> = {
  fired: 'Matched',
  not_fired: 'Did not match',
  skipped: 'Not reached',
  disabled: 'Disabled',
  error: 'Evaluation error',
}

/** What the rule expected of a field, written in the same grammar the decision table uses. */
export function expectedText(comparison: TraceComparison): string {
  const leaf: Leaf = { field: comparison.field, op: comparison.op, value: comparison.expected }
  return renderCell(leaf)
}

/** A computed operand as the trace records it: its value, and the engine's own text of the expression. */
interface Computed {
  value: unknown
  text: string
}

const isComputed = (expected: unknown): expected is Computed =>
  typeof expected === 'object' &&
  expected !== null &&
  !Array.isArray(expected) &&
  'value' in expected &&
  typeof (expected as { text?: unknown }).text === 'string'

/**
 * What the rule expected, in the Expected column: the comparison in the table's grammar and, for an operand the engine
 * computed, its own text of the expression beside the value (Document 3, R-116 in full).
 */
export function expectedParts(comparison: TraceComparison): { cond: string; expression?: string } {
  if (isComputed(comparison.expected)) {
    return {
      cond: expectedText({ ...comparison, expected: comparison.expected.value }),
      expression: comparison.expected.text,
    }
  }
  return { cond: expectedText(comparison) }
}

/** What the case actually carried in that field; a field the case did not carry is said to be absent. */
export function actualText(comparison: TraceComparison): string {
  return comparison.actual === undefined || comparison.actual === null
    ? 'absent'
    : literalText(comparison.actual)
}

/** What a fired rule did, in the engine's words: "set debt_to_income · null → 0.2835", "decide · Approve · terminal". */
export function effectText(action: TraceAction): string {
  if (action.type === 'set') {
    return `set ${action.field ?? ''} · ${literalText(action.from ?? null)} → ${literalText(action.to ?? null)}`
  }
  if (action.type === 'flag') {
    return `flag ${action.code ?? ''}`
  }
  const outcome = ACTION_LABELS[action.outcome ?? 'refer']
  return `decide · ${outcome} · ${action.terminal === false ? 'candidate' : 'terminal'}`
}

/** One cell of the hit map: a rule with its label, what the engine found, and whether it decided the case. */
export interface HitCell {
  ruleId: string
  label: string
  status: TraceStep['status']
  deciding: boolean
}

/** The hit map (the spec, section 09): one cell per rule, in the order the engine walked them. */
export function hitMap(decision: EngineDecision): HitCell[] {
  return decision.trace.map((step) => ({
    ruleId: step.ruleId,
    label: step.label,
    status: step.status,
    deciding: step.status === 'fired' && step.ruleId === decision.decidingRuleId,
  }))
}

/** The hit map's words for a screen reader, in the order of its legend. */
const HIT_WORDS: [TraceStep['status'], string][] = [
  ['fired', 'matched'],
  ['not_fired', 'did not match'],
  ['disabled', 'disabled'],
  ['skipped', 'not reached'],
  ['error', 'evaluation error'],
]

/** "20 rules: 3 matched, 14 did not match, 3 not reached": how many rules took each status. */
export function hitMapLabel(cells: HitCell[]): string {
  const counts = HIT_WORDS.map(
    ([status, word]) => [cells.filter((cell) => cell.status === status).length, word] as const,
  )
    .filter(([count]) => count > 0)
    .map(([count, word]) => `${String(count)} ${word}`)
  return `${String(cells.length)} rule${cells.length === 1 ? '' : 's'}: ${counts.join(', ')}`
}

/** What the collapsed trace leaves to its last row: the rules that did not match and those not reached. */
export function collapsedCounts(decision: EngineDecision): {
  didNotMatch: number
  notReached: number
} {
  return {
    didNotMatch: decision.trace.filter((step) => step.status === 'not_fired').length,
    notReached: decision.trace.filter((step) => step.status === 'skipped').length,
  }
}

/**
 * The Flags section when the engine raised none (the spec, section 09: present even when empty): when the advisory
 * rules, the band that flags, were not reached, the decision came before them, and the sentence says so.
 */
export function noFlags(decision: EngineDecision): string {
  const beforeAdvisory = decision.trace.some(
    (step) => step.status === 'skipped' && bandOf(step.priority).name === 'Advisory',
  )
  return beforeAdvisory ? 'None: the decision came before the advisory rules.' : 'None.'
}
