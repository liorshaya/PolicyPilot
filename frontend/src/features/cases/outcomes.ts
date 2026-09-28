import type { Aggregates, Outcome } from '../../api/types'
import type { DecisionStatus } from '../../shared/ui/decisionLabels'

/**
 * The dashboard's numbers (Document 2, statistics: the counts by outcome and the rules that decided most often).
 * The aggregates come from the engine; this module only puts them in the order a person reads them.
 */

/**
 * The three outcomes in the order the figures read them (the spec, section 09: Approved, Manual review, Declined), so
 * the bar and the figures never reorder between two runs.
 */
export const OUTCOME_ORDER: Outcome[] = ['approve', 'refer', 'reject']

export interface OutcomeCount {
  outcome: DecisionStatus
  count: number
  /** The share of all decisions, 0 to 1; zero decisions is a zero share, never a division by zero. */
  share: number
}

/** The counts by outcome, plus the evaluation errors, over the decisions the version has made. */
export function outcomeCounts(aggregates: Aggregates): OutcomeCount[] {
  const total = aggregates.decisions
  const counted: OutcomeCount[] = OUTCOME_ORDER.map((outcome) => ({
    outcome,
    count: aggregates.outcomes[outcome] ?? 0,
    share: total === 0 ? 0 : (aggregates.outcomes[outcome] ?? 0) / total,
  }))
  return [
    ...counted,
    {
      outcome: 'error',
      count: aggregates.errors,
      share: total === 0 ? 0 : aggregates.errors / total,
    },
  ]
}

/**
 * A share as a percentage with one decimal, as the figures write it (the spec, section 09: "56.5%", "30.0%"). The
 * product is rounded to six decimals first, so a share that ends in a half is not dragged down by binary
 * representation.
 */
export function percent(share: number): string {
  return `${Number((share * 100).toFixed(6)).toFixed(1)}%`
}

/**
 * How long the engine took for one case: whole microseconds below a millisecond, where most lending cases fall, so a
 * fast decision never reads as "0.0 ms"; milliseconds with one decimal from there, the unit of the engine's budget.
 */
export function engineTime(micros: number): string {
  return micros < 1000 ? `${String(Math.round(micros))} µs` : `${(micros / 1000).toFixed(1)} ms`
}
