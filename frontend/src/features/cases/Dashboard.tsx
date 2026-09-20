import type { Aggregates } from '../../api/types'
import { DECISION_LABELS } from '../../shared/ui/decisionLabels'
import { outcomeCounts, percent } from './outcomes'
import './Dashboard.css'

/**
 * What the version has decided so far (Document 2, statistics): the counts by outcome and the rules that decided
 * most often. Every number is the engine's; nothing here is estimated or predicted.
 */
export function Dashboard({ aggregates }: { aggregates: Aggregates }) {
  const counts = outcomeCounts(aggregates)
  return (
    <div className="dashboard">
      <dl className="dashboard__counts">
        {counts.map(({ outcome, count, share }) => (
          <div key={outcome} className={`dashboard__count dashboard__count--${outcome}`}>
            <dt>{DECISION_LABELS[outcome]}</dt>
            <dd>
              <span className="dashboard__number tabular">{count}</span>
              <span className="dashboard__share tabular">{percent(share)}</span>
            </dd>
          </div>
        ))}
      </dl>

      {aggregates.topDecidingRules.length > 0 ? (
        <section className="dashboard__top">
          <h3 className="dashboard__heading">
            Rules that decided most often, of {aggregates.decisions} decisions
          </h3>
          <ol className="dashboard__rules">
            {aggregates.topDecidingRules.map((rule) => (
              <li key={rule.ruleId}>
                <span className="mono">{rule.ruleId}</span>
                <span
                  className="dashboard__bar"
                  style={{
                    inlineSize: barWidth(rule.count, aggregates.topDecidingRules[0]?.count),
                  }}
                  aria-hidden="true"
                />
                <span className="tabular">{rule.count}</span>
              </li>
            ))}
          </ol>
        </section>
      ) : null}
    </div>
  )
}

/** The bar beside a rule is a proportion of the rule that decided most often, never of an invented maximum. */
function barWidth(count: number, largest: number | undefined): string {
  if (largest === undefined || largest === 0) {
    return '0%'
  }
  return `${String(Math.round((count / largest) * 100))}%`
}
