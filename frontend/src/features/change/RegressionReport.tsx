import { useState } from 'react'
import { DECISION_LABELS, type DecisionStatus } from '../../shared/ui/decisionLabels'
import { Button } from '../../shared/ui/Button'
import { DecisionTag } from '../../shared/ui/StatusTag'
import { percent } from '../cases/outcomes'
import type { Flip, Regression } from './types'
import '../../shared/ui/Figures.css'
import '../../shared/ui/Table.css'
import './RegressionReport.css'

interface RegressionReportProps {
  regression: Regression
  /** The version the change was proposed on, whose decisions the copy decided again. */
  baseVersionNo: number
  /** Opens a flipped case's two traces, its stored one and the proposal's; without it the list offers none. */
  onBothTraces?: (flip: Flip) => void
}

/** The outcomes in the order the matrix writes them (the spec, section 09): Approved, Declined, Manual review. */
const OUTCOMES: DecisionStatus[] = ['approve', 'reject', 'refer']

/** How many flips the list shows before "Show all". */
const FIRST = 3

/**
 * The regression report of a proposal (Document 3, Regression report; the spec, section 09, "The change request":
 * "Regression leads with '12 flipped · 6.0% of 200', then the matrix with totals and the unchanged diagonal in ink-3,
 * the hot cells in the selection wash, flips by cause as a one-hue bar that filters the list, flags moved as their own
 * figure, and each flipped case with both traces one click away"). Every number is the engine's report as the API gives
 * it; the matrix's cells are its transitions, and its diagonal the base outcomes the flips leave.
 */
export function RegressionReport({
  regression,
  baseVersionNo,
  onBothTraces,
}: RegressionReportProps) {
  const [cause, setCause] = useState<string | null>(null)
  const [all, setAll] = useState(false)
  const { decisions, flips } = regression
  if (decisions === 0) {
    return (
      <p className="regression__empty">
        {`This sandbox has not decided a case on version ${baseVersionNo} yet, so no decision can flip. Run the cases, then propose the change again.`}
      </p>
    )
  }
  if (flips.length === 0) {
    return <p className="regression__empty">{`None of the ${decisions} decisions flips.`}</p>
  }
  const causes = causesOf(flips)
  const listed = cause === null ? flips : flips.filter((flip) => ruleOf(flip) === cause)
  const shown = all ? listed : listed.slice(0, FIRST)
  return (
    <section className="regression" aria-label="Regression report">
      <div className="figures">
        <div className="figure">
          <span className="figure__label">
            <span className="dot dot--refer" />
            Flipped
          </span>
          <span className="figure__value">
            {flips.length}
            <small>{`${percent(flips.length / decisions)} of ${String(decisions)}`}</small>
          </span>
        </div>
        <div className="figure">
          <span className="figure__label">Unchanged</span>
          <span className="figure__value">
            {decisions - flips.length}
            <small>{percent((decisions - flips.length) / decisions)}</small>
          </span>
        </div>
        {regression.flagsMoved ? (
          <div className="figure">
            <span className="figure__label">Flags moved</span>
            <span className="figure__value">
              {regression.flagsMoved.decisions}
              {topRule(regression.flagsMoved.byRule) ? (
                <small>{topRule(regression.flagsMoved.byRule)}</small>
              ) : null}
            </span>
          </div>
        ) : null}
      </div>
      {regression.before ? (
        <Matrix regression={regression} before={regression.before} baseVersionNo={baseVersionNo} />
      ) : null}
      <div className="barlist" role="group" aria-label="Flips by cause">
        {causes.map(([ruleId, count]) => (
          <button
            key={ruleId}
            type="button"
            className="barlist__row"
            aria-pressed={cause === ruleId}
            onClick={() => setCause(cause === ruleId ? null : ruleId)}
          >
            <span className="barlist__id">{ruleId}</span>
            <span className="barlist__track">
              <span
                className="barlist__fill"
                style={{ inlineSize: percent(count / causes[0]![1]) }}
              />
            </span>
            <span className="barlist__val">
              <b>{count}</b>
              {count === 1 ? ' flip' : ' flips'}
            </span>
          </button>
        ))}
      </div>
      <div className="table-scroll">
        <table
          className="table table--compact table--last-borderless regression__flips"
          aria-label="The decisions that flip"
        >
          <thead>
            <tr>
              <th className="t-num">Case</th>
              <th>{`v${String(baseVersionNo)}`}</th>
              <th>Proposed</th>
              <th>Decided by</th>
              <th>
                <span className="sr-only">Traces</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {shown.map((flip) => (
              <tr key={flip.decisionId}>
                <td className="t-num">{flip.caseNo ?? 'Own case'}</td>
                <td>
                  <DecisionTag status={flip.before} quiet />
                </td>
                <td>
                  <DecisionTag status={flip.after} quiet />
                </td>
                <td className="t-id">{`${flip.decidingRuleBefore ?? 'none'} → ${flip.decidingRuleAfter ?? 'none'}`}</td>
                <td>
                  {onBothTraces ? (
                    <Button variant="link" onClick={() => onBothTraces(flip)}>
                      Both traces
                    </Button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
          {listed.length > shown.length ? (
            <tfoot>
              <tr>
                <td colSpan={5} className="muted">
                  {`${String(listed.length - shown.length)} more`} ·{' '}
                  <Button variant="link" onClick={() => setAll(true)}>
                    {`Show all ${String(listed.length)}`}
                  </Button>
                </td>
              </tr>
            </tfoot>
          ) : null}
        </table>
      </div>
    </section>
  )
}

/**
 * The matrix of the base outcomes by the proposal's (the spec's "v1 → proposed"): a flipped cell is its transition's
 * count, the diagonal what each base outcome keeps, and the totals are the base outcomes by row and the proposal's by
 * column. A report stored before it counted its base outcomes has no matrix.
 */
function Matrix({
  regression,
  before,
  baseVersionNo,
}: {
  regression: Regression
  before: Record<string, number>
  baseVersionNo: number
}) {
  const withError =
    (before.error ?? 0) > 0 ||
    Object.keys(regression.transitions).some((key) => key.includes('error'))
  const outcomes: DecisionStatus[] = withError ? [...OUTCOMES, 'error'] : OUTCOMES
  const moved = (from: DecisionStatus, to: DecisionStatus) =>
    regression.transitions[`${from} → ${to}`] ?? 0
  const cell = (from: DecisionStatus, to: DecisionStatus) =>
    from === to
      ? (before[from] ?? 0) -
        outcomes
          .filter((other) => other !== from)
          .reduce((sum, other) => sum + moved(from, other), 0)
      : moved(from, to)
  return (
    // a table may be wider than the window only in a box of its own that scrolls (the spec, section 10, the phone)
    <div className="table-scroll">
      <table className="matrix" aria-label={`Version ${String(baseVersionNo)} by the proposal`}>
        <thead>
          <tr>
            <th className="rowhead">{`v${String(baseVersionNo)} → proposed`}</th>
            {outcomes.map((outcome) => (
              <th key={outcome}>{DECISION_LABELS[outcome]}</th>
            ))}
            <th className="total">Total</th>
          </tr>
        </thead>
        <tbody>
          {outcomes.map((from) => (
            <tr key={from}>
              <th className="rowhead">
                <DecisionTag status={from} quiet />
              </th>
              {outcomes.map((to) => (
                <td
                  key={to}
                  className={from === to ? 'same' : cell(from, to) > 0 ? 'hot' : undefined}
                >
                  {cell(from, to)}
                </td>
              ))}
              <td className="total">{before[from] ?? 0}</td>
            </tr>
          ))}
          <tr>
            <th className="rowhead total">Total</th>
            {outcomes.map((to) => (
              <td key={to} className="total">
                {outcomes.reduce((sum, from) => sum + cell(from, to), 0)}
              </td>
            ))}
            <td className="total">{regression.decisions}</td>
          </tr>
        </tbody>
      </table>
    </div>
  )
}

/** The rule a flipped decision now answers to: the proposal's deciding rule, or none. */
function ruleOf(flip: Flip): string {
  return flip.decidingRuleAfter ?? 'none'
}

/** The flips by cause, the rule that decides each case on the proposal, the most first, then by id. */
function causesOf(flips: Flip[]): [string, number][] {
  const counts = new Map<string, number>()
  for (const flip of flips) {
    counts.set(ruleOf(flip), (counts.get(ruleOf(flip)) ?? 0) + 1)
  }
  return [...counts].sort(([a, left], [b, right]) => right - left || (a < b ? -1 : 1))
}

/** The rule whose flags moved in the most decisions, which the figure names beside the count. */
function topRule(byRule: Record<string, number>): string | undefined {
  return Object.entries(byRule).sort(
    ([a, left], [b, right]) => right - left || (a < b ? -1 : 1),
  )[0]?.[0]
}
