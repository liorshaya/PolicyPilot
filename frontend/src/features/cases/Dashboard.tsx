import { useState } from 'react'
import type { Aggregates, Rule } from '../../api/types'
import { Button } from '../../shared/ui/Button'
import { DECISION_LABELS, type DecisionStatus } from '../../shared/ui/decisionLabels'
import { decisionOf } from '../rules/tableModel'
import { outcomeCounts, percent } from './outcomes'
import './Dashboard.css'

/** The spec's class for each outcome: its segment, its dot. */
const TAGS: Record<DecisionStatus, string> = {
  approve: 'approve',
  reject: 'decline',
  refer: 'refer',
  error: 'error',
}

interface DashboardProps {
  aggregates: Aggregates
  /** The rules of the version, for the outcome each one decides: the dot beside it in the list. */
  rules: Rule[]
  /** The deciding rule the case list is filtered by, if any. */
  filter: string | null
  onFilter: (ruleId: string | null) => void
}

/**
 * The outcome row over the case list (the spec, section 09, "Figures"; Document 2, statistics): one proportional bar,
 * three figures with their share, the evaluation errors as a footnote, and the rules that decided most often as a
 * one-hue bar list whose rows filter the list. Every number is the engine's; nothing here is estimated or predicted.
 */
export function Dashboard({ aggregates, rules, filter, onFilter }: DashboardProps) {
  const [declinesOnly, setDeclinesOnly] = useState(false)
  const counts = outcomeCounts(aggregates)
  const errors = aggregates.errors
  const outcomeOf = new Map(rules.map((rule) => [rule.id, decisionOf(rule)]))
  const top = aggregates.topDecidingRules
  const largest = Math.max(0, ...top.map((rule) => rule.count))
  const shown = top.filter((rule) => !declinesOnly || outcomeOf.get(rule.ruleId) === 'reject')
  const errorWords = `${String(errors)} evaluation error${errors === 1 ? '' : 's'}`
  const barLabel = [
    ...counts
      .filter(({ outcome }) => outcome !== 'error')
      .map(({ outcome, count }) => `${String(count)} ${DECISION_LABELS[outcome].toLowerCase()}`),
    errorWords,
  ].join(', ')
  return (
    <>
      <div className="outcome">
        <div className="outcome__bar" role="img" aria-label={barLabel}>
          {counts
            .filter(({ count }) => count > 0)
            .map(({ outcome, share }) => (
              <span
                key={outcome}
                className={`outcome__seg outcome__seg--${TAGS[outcome]}`}
                style={{ inlineSize: percent(share) }}
              />
            ))}
        </div>
        <dl className="figures">
          {counts
            .filter(({ outcome }) => outcome !== 'error')
            .map(({ outcome, count, share }) => (
              <div key={outcome} className="figure">
                <dt className="figure__label">
                  <span className={`dot dot--${TAGS[outcome]}`} />
                  {DECISION_LABELS[outcome]}
                </dt>
                <dd className="figure__value">
                  {count}
                  <small>{percent(share)}</small>
                </dd>
              </div>
            ))}
        </dl>
        <span className="figure__foot">{errorWords}</span>
      </div>

      {top.length > 0 ? (
        <div className="dashboard__rules">
          <div className="barlist__title">
            Rules that decided most often · click to filter the list
            <Button
              variant="link"
              aria-pressed={declinesOnly}
              onClick={() => setDeclinesOnly(!declinesOnly)}
            >
              Declines only
            </Button>
          </div>
          <div className="barlist">
            {shown.map(({ ruleId, count }) => {
              const outcome = outcomeOf.get(ruleId)
              return (
                <button
                  key={ruleId}
                  type="button"
                  className="barlist__row"
                  aria-pressed={filter === ruleId}
                  onClick={() => onFilter(filter === ruleId ? null : ruleId)}
                >
                  <span className="barlist__id">
                    <span className={`dot dot--${outcome ? TAGS[outcome] : 'none'}`} />
                    {ruleId}
                  </span>
                  <span className="barlist__track">
                    <span
                      className="barlist__fill"
                      style={{ inlineSize: `${((count / largest) * 100).toFixed(1)}%` }}
                    />
                  </span>
                  <span className="barlist__val">
                    <b>{count}</b> · {percent(count / aggregates.decisions)}
                  </span>
                </button>
              )
            })}
          </div>
        </div>
      ) : null}
    </>
  )
}
