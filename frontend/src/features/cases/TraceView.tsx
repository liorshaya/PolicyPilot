import type { Decision, TraceStep } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { DecisionTag } from '../../shared/ui/StatusTag'
import { literalText } from '../rules/cellGrammar'
import { actualText, expectedText, numbered, STEP_LABELS } from './trace'
import './TraceView.css'

interface TraceViewProps {
  decision: Decision
  /** The language of the policy, for the reason a rule gives the applicant. */
  language: ContentLanguage
  /** The rule whose row is selected elsewhere; its step carries the same blue bar. */
  selectedRuleId?: string | null
  onSelectRule?: (ruleId: string) => void
}

/**
 * The decision trace (Document 3, Trace format): every rule the engine walked, in order, with what it compared,
 * what the case carried and what it concluded. This is the whole of the explanation: no model wrote any of it.
 */
export function TraceView({
  decision,
  language,
  selectedRuleId = null,
  onSelectRule,
}: TraceViewProps) {
  return (
    <div className="trace">
      <div className="trace__outcome">
        {decision.status === 'ERROR' ? (
          <DecisionTag status="error" />
        ) : (
          <DecisionTag status={decision.outcome ?? 'refer'} />
        )}
        {decision.reason ? (
          <p className="trace__reason" {...contentAttributes(language)}>
            {decision.reason}
          </p>
        ) : null}
        {decision.errorCode ? (
          <p className="trace__reason">
            <span className="mono">{decision.errorCode}</span>
            {decision.errorRuleId ? <span className="mono"> · {decision.errorRuleId}</span> : null}
          </p>
        ) : null}
      </div>

      {Object.keys(decision.derived).length > 0 ? (
        <section className="trace__derived">
          <h3 className="trace__heading">Derived by the engine</h3>
          <dl className="trace__values">
            {Object.entries(decision.derived).map(([field, value]) => (
              <div key={field}>
                <dt className="mono">{field}</dt>
                <dd className="tabular">{literalText(value)}</dd>
              </div>
            ))}
          </dl>
        </section>
      ) : null}

      {decision.flags.length > 0 ? (
        <section>
          <h3 className="trace__heading">Flags</h3>
          <ul className="trace__flags">
            {decision.flags.map((flag) => (
              <li key={flag.code}>
                <span className="mono">{flag.code}</span>
                <span className="trace__flag-rule mono">{flag.ruleId}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <section>
        <h3 className="trace__heading">Steps, in the order the engine walked them</h3>
        <ol className="trace__steps">
          {numbered(decision.trace).map(({ no, step }) => (
            <Step
              key={`${String(no)}-${step.ruleId}`}
              no={no}
              step={step}
              selected={step.ruleId === selectedRuleId}
              onSelect={onSelectRule}
            />
          ))}
        </ol>
      </section>
    </div>
  )
}

function Step({
  no,
  step,
  selected,
  onSelect,
}: {
  no: number
  step: TraceStep
  selected: boolean
  onSelect?: (ruleId: string) => void
}) {
  const comparisons = step.comparisons ?? []
  return (
    <li
      className={`trace__step trace__step--${step.status}${selected ? ' trace__step--selected' : ''}`}
      aria-current={selected ? 'true' : undefined}
    >
      <div className="trace__step-head">
        <span className="trace__no tabular" aria-hidden="true">
          {no}
        </span>
        {onSelect ? (
          <button type="button" className="trace__rule" onClick={() => onSelect(step.ruleId)}>
            <span className="mono">{step.ruleId}</span>
          </button>
        ) : (
          <span className="mono">{step.ruleId}</span>
        )}
        <bdi className="trace__label" dir="auto">
          {step.label}
        </bdi>
        <span className="trace__status">{STEP_LABELS[step.status]}</span>
      </div>

      {comparisons.length > 0 ? (
        <table className="trace__comparisons">
          <caption className="sr-only">What step {no} compared</caption>
          <thead>
            <tr>
              <th scope="col">Field</th>
              <th scope="col">Expected</th>
              <th scope="col">In the case</th>
              <th scope="col">Result</th>
            </tr>
          </thead>
          <tbody>
            {comparisons.map((comparison) => (
              <tr key={`${comparison.field}${comparison.op}`}>
                <td className="mono">{comparison.field}</td>
                <td className="tabular">{expectedText(comparison)}</td>
                <td className="tabular">{actualText(comparison)}</td>
                <td
                  className={
                    comparison.result ? 'trace__result trace__result--true' : 'trace__result'
                  }
                >
                  {comparison.result ? 'met' : 'not met'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      {step.error ? (
        <p className="trace__error">
          <span className="mono">{step.error.code}</span> {step.error.detail}
        </p>
      ) : null}
    </li>
  )
}
