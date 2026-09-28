import { useId, useState, type MouseEvent, type ReactElement } from 'react'
import { api, ApiError, decisionExportUrl } from '../../api/client'
import { useExplain } from '../../api/queries'
import type { Decision, FieldSchema, ProposedDecision, TraceStep } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { dateTimeOf, timeOf } from '../../shared/i18n/time'
import { Actor } from '../../shared/ui/Actor'
import { Button } from '../../shared/ui/Button'
import { Chip } from '../../shared/ui/Chip'
import { Icon } from '../../shared/ui/Icon'
import { Provenance } from '../../shared/ui/Provenance'
import { DecisionTag } from '../../shared/ui/StatusTag'
import { saveFile } from '../../shared/ui/saveFile'
import type { DecisionStatus } from '../../shared/ui/decisionLabels'
import { changeRequestName } from '../change/names'
import { literalText, unitLabel } from '../rules/cellGrammar'
import { ExplainActions, Explanation } from './ExplainPanel'
import { engineTime } from './outcomes'
import {
  actualText,
  collapsedCounts,
  effectText,
  expectedParts,
  hitMap,
  hitMapLabel,
  noFlags,
  STEP_LABELS,
} from './trace'
import './TraceView.css'

/**
 * Who decided, on what and when: "decided on v1 · engine · 61 µs · 10:14:07" for a stored decision, the time of day as
 * the spec's Cases screen writes it in the margin with the date on hover and in the markup; "decided on CR-0001 ·
 * engine · not stored" for what a proposal decides, which has no time to give.
 */
function provenanceOf(decision: Decision | ProposedDecision): ReactElement[] {
  if ('changeRequestNumber' in decision) {
    return [
      <span key="request">
        decided on <b>{changeRequestName(decision.changeRequestNumber)}</b>
      </span>,
      <Actor key="engine" kind="engine">
        engine
      </Actor>,
      <span key="stored">not stored</span>,
    ]
  }
  return [
    <span key="version">
      decided on <b>v{decision.rulesetVersion.versionNo}</b>
    </span>,
    <Actor key="engine" kind="engine">
      engine · {engineTime(decision.durationMicros)}
    </Actor>,
    <time key="time" dateTime={decision.decidedAt} title={dateTimeOf(decision.decidedAt, true)}>
      {timeOf(decision.decidedAt, true)}
    </time>,
  ]
}

/** The spec's class for each outcome: the tag, the dot of the deciding step, the ring of its cell. */
const TAGS: Record<DecisionStatus, string> = {
  approve: 'approve',
  reject: 'decline',
  refer: 'refer',
  error: 'error',
}

/** The line style of each step's mark, by what the engine found (the spec, section 09). */
const STEP_CLASSES: Record<TraceStep['status'], string> = {
  fired: ' step--matched',
  not_fired: '',
  skipped: ' step--notreached',
  disabled: ' step--disabled',
  error: '',
}

/** The hit map's cell for each status; a rule that did not match is the plain outline. */
const CELL_CLASSES: Record<TraceStep['status'], string> = {
  fired: ' hitmap__cell--matched',
  not_fired: '',
  skipped: ' hitmap__cell--notreached',
  disabled: ' hitmap__cell--disabled',
  error: '',
}

interface TraceViewProps {
  /** A stored decision, or what a proposal decides for one (Document 2, the proposed side of a flipped case). */
  decision: Decision | ProposedDecision
  /** The case a proposal's decision is about, which its answer does not carry: it names the stored decision instead. */
  caseNo?: number | null
  /** The language of the policy, for the labels and the reason a rule gives the applicant. */
  language: ContentLanguage
  /** The fields of the version that decided, for the unit of each value the engine derived. */
  fields: FieldSchema[]
  onClose?: () => void
  /** Opens a rule the trace names on the Rules screen. */
  onSelectRule?: (ruleId: string) => void
}

/**
 * The engine's record of one decision (Document 3, Trace format; the spec, section 09, "The trace"), rendered verbatim
 * and in the engine's own words (Document 2, key decision 4): the head with the outcome, the provenance and the hit map;
 * the explanation a reader asks for; the rule that decided with its reason; what the engine derived and flagged; and the
 * steps in the order the engine walked them. Nothing on this surface is computed by the client. What a proposal decides
 * reads the same, headed by the request it was decided on; it was never stored, so it has no time, and nothing to
 * explain or to export.
 */
export function TraceView({
  decision,
  caseNo,
  language,
  fields,
  onClose,
  onSelectRule,
}: TraceViewProps) {
  const stepsId = useId()
  const stored = 'changeRequestNumber' in decision ? null : decision
  const explain = useExplain(stored?.id ?? null)
  const [everyComparison, setEveryComparison] = useState(false)
  const [exportRefused, setExportRefused] = useState<string | null>(null)
  const failed = decision.status === 'ERROR'
  const outcome: DecisionStatus = failed ? 'error' : (decision.outcome ?? 'refer')
  const cells = hitMap(decision)
  const deciding = decision.trace.find(
    (step) => step.status === 'fired' && step.ruleId === decision.decidingRuleId,
  )
  const units = new Map(fields.map((field) => [field.name, field.unit]))
  const derived = Object.entries(decision.derived)
  const { didNotMatch, notReached } = collapsedCounts(decision)
  const collapsing = !everyComparison && didNotMatch + notReached > 0
  const steps = everyComparison
    ? decision.trace
    : decision.trace.filter((step) => step.status !== 'skipped')
  // the comparisons' column heads stand over the first comparisons shown, as the spec draws them
  const headed = steps.find(
    (step) =>
      (step.comparisons ?? []).length > 0 && (everyComparison || step.status !== 'not_fired'),
  )?.ruleId
  const chip = (ruleId: string, active = false) => (
    <Chip active={active} onClick={onSelectRule ? () => onSelectRule(ruleId) : undefined}>
      {ruleId}
    </Chip>
  )

  async function save(
    stored: Decision,
    accept: 'application/json' | 'text/csv',
    extension: string,
  ) {
    setExportRefused(null)
    try {
      const { blob, name } = await api.exportDecision(stored.id, accept)
      saveFile(blob, name ?? `case-${String(stored.caseNo ?? stored.id)}.${extension}`)
    } catch (error) {
      setExportRefused(error instanceof ApiError ? error.code : 'NETWORK_ERROR')
    }
  }

  const exportAs =
    (stored: Decision, accept: 'application/json' | 'text/csv', extension: string) =>
    (event: MouseEvent<HTMLAnchorElement>) => {
      event.preventDefault()
      void save(stored, accept, extension)
    }

  return (
    <div className="trace">
      <div className="trace__head">
        <div className="trace__title">
          <h2 className="trace__case">
            Case <span className="tabular">{(stored ? stored.caseNo : caseNo) ?? '—'}</span>
          </h2>
          <span className="trace__side">
            <DecisionTag status={outcome} />
            {onClose ? (
              <Button variant="quiet" size="sm" onClick={onClose}>
                Close
              </Button>
            ) : null}
          </span>
        </div>
        <Provenance segments={provenanceOf(decision)} />
        <div>
          <div
            className={`hitmap${failed ? '' : ` hitmap--${TAGS[outcome]}`}`}
            role="img"
            aria-label={hitMapLabel(cells)}
          >
            {cells.map((cell) => (
              <span
                key={cell.ruleId}
                className={`hitmap__cell${cell.deciding ? ' hitmap__cell--deciding' : CELL_CLASSES[cell.status]}`}
                title={`${cell.ruleId} · ${cell.deciding ? 'decided' : STEP_LABELS[cell.status]}`}
              />
            ))}
          </div>
          <div className={`hitmap__legend${failed ? '' : ` hitmap--${TAGS[outcome]}`}`}>
            <span>
              <span className="hitmap__cell hitmap__cell--matched" />
              matched
            </span>
            <span>
              <span className="hitmap__cell" />
              did not match
            </span>
            <span>
              <span className="hitmap__cell hitmap__cell--notreached" />
              not reached
            </span>
            <span>
              <span className="hitmap__cell hitmap__cell--disabled" />
              disabled
            </span>
            <span>
              <span className="hitmap__cell hitmap__cell--deciding" />
              decided, ringed in its outcome
            </span>
          </div>
        </div>
        {stored ? (
          <div className="btn-group">
            <ExplainActions explain={explain} />
            <span className="muted trace__export">
              Export{' '}
              <a
                href={decisionExportUrl(stored.id)}
                onClick={exportAs(stored, 'application/json', 'json')}
              >
                JSON
              </a>{' '}
              ·{' '}
              <a href={decisionExportUrl(stored.id)} onClick={exportAs(stored, 'text/csv', 'csv')}>
                CSV
              </a>
            </span>
            {exportRefused ? (
              <span className="reason" role="alert">
                The export was refused (<span className="mono">{exportRefused}</span>).
              </span>
            ) : null}
          </div>
        ) : null}
      </div>

      {stored ? (
        <Explanation explain={explain} language={language} onOpenRule={onSelectRule} />
      ) : null}

      {failed ? (
        <div className="trace__deciding">
          <span className="trace__deciding-label">Evaluation error</span>
          <span className="mono">
            {decision.errorCode}
            {decision.errorRuleId ? ` · ${decision.errorRuleId}` : ''}
          </span>
        </div>
      ) : deciding ? (
        <div className="trace__deciding">
          <span className="trace__deciding-label">Decided by</span>
          <div className="trace__decided">
            {chip(deciding.ruleId, true)}
            <span className="step__label" {...contentAttributes(language)}>
              {deciding.label}
            </span>
            {decision.reason ? (
              <>
                <span className="muted trace__reason-label">Reason for the applicant</span>
                <p className="trace__reason" {...contentAttributes(language)}>
                  {decision.reason}
                </p>
              </>
            ) : null}
          </div>
        </div>
      ) : null}

      <div className="trace__h">Derived by the engine</div>
      {derived.length > 0 ? (
        <dl className="trace__derived">
          {derived.map(([field, value]) => {
            const unit = units.get(field)
            return (
              <div key={field}>
                <dt className="mono">{field}</dt>
                <dd className="num">
                  {`${literalText(value)}${unit && value !== null ? ` ${unitLabel(unit)}` : ''}`}
                </dd>
              </div>
            )
          })}
        </dl>
      ) : (
        <p className="trace__none">None.</p>
      )}

      <div className="trace__h">Flags</div>
      {decision.flags.length > 0 ? (
        <div className="trace__derived">
          {decision.flags.map((flag) => (
            <div key={flag.code}>
              <span className="mono">{flag.code}</span>
              {chip(flag.ruleId)}
              <div className="he-quote">
                <p {...contentAttributes(language)}>{flag.message}</p>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p className="trace__none">{noFlags(decision)}</p>
      )}

      <div className="trace__h" id={stepsId}>
        Steps, in the order the engine walked them
      </div>
      <ol className="trace__steps" aria-labelledby={stepsId}>
        {steps.map((step) => {
          const decided = step === deciding
          const comparisons = step.comparisons ?? []
          const shown = everyComparison || step.status !== 'not_fired'
          return (
            <li key={step.ruleId} className={`step${STEP_CLASSES[step.status]}`}>
              <span className="step__mark" />
              <div className="step__head">
                {chip(step.ruleId, decided)}
                <span className="step__label" {...contentAttributes(language)}>
                  {step.label}
                </span>
                <span className="step__status">
                  {decided ? <span className={`dot dot--${TAGS[outcome]}`} /> : null}
                  {decided ? 'Matched · decided' : STEP_LABELS[step.status]}
                </span>
              </div>
              {shown && comparisons.length > 0 ? (
                <div className="cmp">
                  {step.ruleId === headed ? (
                    <>
                      <span className="cmp__h">Field</span>
                      <span className="cmp__h cmp__h--end">Expected</span>
                      <span className="cmp__h cmp__h--end">In the case</span>
                      <span className="cmp__h">Result</span>
                    </>
                  ) : null}
                  {comparisons.flatMap((comparison) => {
                    const { cond, expression } = expectedParts(comparison)
                    const key = `${comparison.field} ${comparison.op} ${JSON.stringify(comparison.expected) ?? ''}`
                    return [
                      <span key={`${key} field`} className="cmp__field">
                        {comparison.field}
                      </span>,
                      <span key={`${key} cond`} className="cmp__cond">
                        {cond}
                        {expression ? <span className="muted"> {expression}</span> : null}
                      </span>,
                      <span key={`${key} actual`} className="cmp__actual">
                        {actualText(comparison)}
                      </span>,
                      <span
                        key={`${key} result`}
                        className={`cmp__result cmp__result--${comparison.result ? 'true' : 'false'}`}
                      >
                        <Icon name={comparison.result ? 'check' : 'cross'} />
                        {comparison.result ? 'met' : 'not met'}
                      </span>,
                    ]
                  })}
                </div>
              ) : null}
              {(step.actions ?? []).map((action) => (
                <div key={effectText(action)} className="step__effect">
                  {effectText(action)}
                </div>
              ))}
              {step.error ? (
                <div className="step__effect">
                  {step.error.code} · {step.error.detail}
                </div>
              ) : null}
            </li>
          )
        })}
      </ol>
      {collapsing ? (
        <div className="step step--notreached step--foot">
          <span className="step__mark" />
          <div className="step__head">
            <span>
              {[
                didNotMatch > 0
                  ? didNotMatch === 1
                    ? '1 rule that did not match is collapsed to its head'
                    : `${String(didNotMatch)} rules that did not match are collapsed to their head`
                  : null,
                notReached > 0 ? `${String(notReached)} not reached after the decision` : null,
              ]
                .filter((part) => part !== null)
                .join(' · ')}{' '}
              ·{' '}
              <Button variant="link" onClick={() => setEveryComparison(true)}>
                Show every comparison
              </Button>
            </span>
          </div>
        </div>
      ) : null}
    </div>
  )
}
