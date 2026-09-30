import { useState } from 'react'
import type { Aggregates, Rule } from '../../api/types'
import { Button } from '../../shared/ui/Button'
import { DECISION_LABELS, type DecisionStatus } from '../../shared/ui/decisionLabels'
import { decisionOf } from '../rules/tableModel'
import { OUTCOME_ORDER, outcomeCounts, percent } from './outcomes'
import '../../shared/ui/Figures.css'
import './Dashboard.css'

/** The spec's class for each outcome: its segment, its dot. */
const TAGS: Record<DecisionStatus, string> = {
  approve: 'approve',
  reject: 'decline',
  refer: 'refer',
  error: 'error',
}

interface DashboardProps {
  /** The version's statistics; while they are read, the figures stand with a dash for each number (section 11). */
  aggregates: Aggregates | undefined
  /** The rules of the version, for the outcome each one decides: the dot beside it in the list. */
  rules: Rule[]
  /** The deciding rule the case list is filtered by, if any. */
  filter: string | null
  onFilter: (ruleId: string | null) => void
}

/**
 * The summary band over the case list (the spec, section 09, "Figures"; Document 2, statistics): one proportional bar,
 * three figures with their share and the evaluation errors as a footnote, and beside them, while the sheet is wide
 * enough, the rules that decided most often as a one-hue bar list whose rows filter the list. Every number is the
 * engine's; nothing here is estimated or predicted.
 */
export function Dashboard({ aggregates, rules, filter, onFilter }: DashboardProps) {
  const [declinesOnly, setDeclinesOnly] = useState(false)
  if (aggregates === undefined) {
    return <PendingFigures />
  }
  const counts = outcomeCounts(aggregates)
  const errors = aggregates.errors
  const outcomeOf = new Map(rules.map((rule) => [rule.id, decisionOf(rule)]))
  const labelOf = new Map(rules.map((rule) => [rule.id, rule.label]))
  const top = aggregates.topDecidingRules
  const largest = Math.max(0, ...top.map((rule) => rule.count))
  const shown = top.filter((rule) => !declinesOnly || outcomeOf.get(rule.ruleId) === 'reject')
  const errorWords = `${String(errors)} evaluation error${errors === 1 ? '' : 's'}`
  const words = ({ outcome, count }: { outcome: DecisionStatus; count: number }) =>
    outcome === 'error' ? errorWords : `${String(count)} ${DECISION_LABELS[outcome].toLowerCase()}`
  const barLabel = [
    ...counts.filter(({ outcome }) => outcome !== 'error').map(words),
    errorWords,
  ].join(', ')
  return (
    <div className="summary">
      <div className="outcome">
        <div className="outcome__bar" role="img" aria-label={barLabel}>
          {counts
            .filter(({ count }) => count > 0)
            .map((counted) => (
              <span
                key={counted.outcome}
                className={`outcome__seg outcome__seg--${TAGS[counted.outcome]}`}
                style={{ inlineSize: percent(counted.share) }}
                title={`${words(counted)} · ${percent(counted.share)}`}
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
        <div className="summary__rules">
          <div className="barlist__title">
            Rules that decided most often · click to filter the list
            <Button
              variant="secondary"
              size="sm"
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
                  title={labelOf.get(ruleId)}
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
    </div>
  )
}

/**
 * The outcome row while the statistics are read (the spec, section 11: "the figures show dashes"): the three figures
 * under their words, a dash where each number will stand, and nothing drawn in the bar.
 */
function PendingFigures() {
  return (
    <div className="summary">
      <div className="outcome">
        <div className="outcome__bar" aria-hidden="true" />
        <dl className="figures">
          {OUTCOME_ORDER.map((outcome) => (
            <div key={outcome} className="figure">
              <dt className="figure__label">
                <span className={`dot dot--${TAGS[outcome]}`} />
                {DECISION_LABELS[outcome]}
              </dt>
              <dd className="figure__value">—</dd>
            </div>
          ))}
        </dl>
      </div>
    </div>
  )
}
