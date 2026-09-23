import type { DecisionStatus } from '../../shared/ui/decisionLabels'
import { DecisionTag } from '../../shared/ui/StatusTag'
import type { Regression } from './types'
import './RegressionReport.css'

interface RegressionReportProps {
  regression: Regression
  /** The version the change was proposed on, whose decisions the copy decided again. */
  baseVersionNo: number
}

/**
 * The regression report of a proposal (Document 3, Regression report): every decision the sandbox made on the base
 * version, decided again by the patched copy. Each flip is listed by application number with both outcomes and both
 * deciding rules, and each transition is counted. The API orders the flips by case number and the transitions by key,
 * and this renders them as they come: the report is the engine's, and the screen adds nothing to it.
 */
export function RegressionReport({ regression, baseVersionNo }: RegressionReportProps) {
  if (regression.decisions === 0) {
    return (
      <p className="regression__empty">
        {`This sandbox has not decided a case on version ${baseVersionNo} yet, so no decision can flip. Run the cases, then propose the change again.`}
      </p>
    )
  }
  if (regression.flips.length === 0) {
    return (
      <p className="regression__empty">{`None of the ${regression.decisions} decisions flips.`}</p>
    )
  }
  return (
    <section className="regression" aria-label="Regression report">
      <p className="regression__summary">
        {`${regression.flips.length} of the ${regression.decisions} decisions made on version ${baseVersionNo} flip.`}
      </p>
      <ul className="regression__transitions" aria-label="Transitions">
        {Object.entries(regression.transitions).map(([key, count]) => {
          // keyed "approve → reject" (Document 3), each side one of the engine's outcomes or error
          const [before, after] = key.split(' → ') as [DecisionStatus, DecisionStatus]
          return (
            <li key={key} className="regression__transition">
              <DecisionTag status={before} quiet /> → <DecisionTag status={after} quiet />{' '}
              <span className="regression__count tabular">{count}</span>
            </li>
          )
        })}
      </ul>
      <div className="regression__scroll">
        <table className="regression__table">
          <caption className="sr-only">The decisions that flip, by application number</caption>
          <thead>
            <tr>
              <th scope="col">Application</th>
              <th scope="col">Before</th>
              <th scope="col">After</th>
              <th scope="col">Decided by, before → after</th>
            </tr>
          </thead>
          <tbody>
            {regression.flips.map((flip) => (
              <tr key={flip.decisionId}>
                <td className="tabular">{flip.caseNo ?? 'Own case'}</td>
                <td>
                  <DecisionTag status={flip.before} quiet />
                </td>
                <td>
                  <DecisionTag status={flip.after} quiet />
                </td>
                <td>
                  <span className="mono">{flip.decidingRuleBefore ?? 'none'}</span> →{' '}
                  <span className="mono">{flip.decidingRuleAfter ?? 'none'}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
