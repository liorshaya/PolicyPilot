import type { TraceComparison, TraceStep } from '../../api/types'
import { literalText, renderCell, type Leaf } from '../rules/cellGrammar'

/**
 * The trace as a person reads it (Document 3, Trace format): the steps the engine walked, in the order it walked
 * them, with what each one compared and what it concluded. Nothing here decides anything; it names what the engine
 * already decided.
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

/** What the case actually carried in that field; a field the case did not carry is said to be absent. */
export function actualText(comparison: TraceComparison): string {
  return comparison.actual === undefined || comparison.actual === null
    ? 'absent'
    : literalText(comparison.actual)
}

/**
 * The steps to show and the step that decided the case. The engine writes every rule it walked; a trace is read
 * from the top, so the steps keep the engine's own order and are numbered from one.
 */
export function numbered(steps: TraceStep[]): { no: number; step: TraceStep }[] {
  return steps.map((step, index) => ({ no: index + 1, step }))
}
