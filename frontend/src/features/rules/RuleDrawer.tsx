import { useId } from 'react'
import type { Finding, GapResolution, ReviewFinding, Rule } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { Actor } from '../../shared/ui/Actor'
import { Button } from '../../shared/ui/Button'
import { Chip } from '../../shared/ui/Chip'
import { DecisionTag, VersionTag } from '../../shared/ui/StatusTag'
import type { VersionStatus } from '../../shared/ui/decisionLabels'
import '../../shared/ui/Field.css'
import { Paragraph } from '../policy/Paragraph'
import { conditionText } from './cellGrammar'
import { FindingItem } from './ReviewPanel'
import { actionText, bandOf, decisionOf } from './tableModel'
import './RuleDrawer.css'

interface RuleDrawerProps {
  rule: Rule
  language: ContentLanguage
  /** The state of the version the rule belongs to. */
  versionStatus: VersionStatus
  /** The paragraph the rule cites, when the policy is loaded. */
  paragraph?: { index: number; text: string }
  /** The rules that cite the same paragraph, the chosen one among them. */
  citedBy?: string[]
  /** The validator's findings anchored to this rule (Document 3, Error reporting shape). */
  findings: Finding[]
  /** The reviewer's findings that name this rule, of the review's total. */
  reviewFindings: ReviewFinding[]
  reviewTotal: number
  /** A rule of a draft can be switched off, and its findings acknowledged. */
  editable: boolean
  acknowledging: string | null
  onAcknowledge: (findingId: string, resolution?: GapResolution, note?: string) => void
  onSelectRule: (ruleId: string) => void
  onShowParagraph: (index: number) => void
  /** How many cases the rule decided in the last run, when the statistics name it. */
  decided?: { count: number; decisions: number }
  /** The version the rule stands in unchanged since, or where it changed: "v1 · unchanged". */
  since?: string
  /** Back to the whole review, from the findings on this rule. */
  onOpenReview?: () => void
  /** Switches the rule on or off in the draft (Document 3: a rule that is not enabled is skipped). */
  onToggleEnabled?: (enabled: boolean) => void
}

/**
 * The Rules margin with a rule chosen (the spec, section 10): the rule stacked label over value, the paragraph it cites
 * with the quoted span marked, and the findings that name it. The source is beside the rule, because that pairing is
 * what makes a rule auditable (Document 3, Provenance).
 */
export function RuleDrawer({
  rule,
  language,
  versionStatus,
  paragraph,
  citedBy = [],
  findings,
  reviewFindings,
  reviewTotal,
  editable,
  acknowledging,
  onAcknowledge,
  onSelectRule,
  onShowParagraph,
  decided,
  since,
  onOpenReview,
  onToggleEnabled,
}: RuleDrawerProps) {
  const enabledId = useId()
  const decision = decisionOf(rule)
  const decide = rule.actions.find((action) => action.type === 'decide')
  const reason = rule.actions.find((action) => action.reason !== undefined)?.reason
  const band = bandOf(rule.priority)
  const provenance = rule.provenance
  return (
    <>
      <section className="margin__section" aria-label={`Rule ${rule.id}`}>
        <div className="margin__title">
          <span>
            Rule <Chip active>{rule.id}</Chip>
          </span>
          <VersionTag status={versionStatus} />
        </div>
        <p className="rule__label" {...contentAttributes(language)}>
          {rule.label}
        </p>
        <dl className="kv">
          <div>
            <dt>Condition</dt>
            {/* Document 3's grammar, the diff's own; the table's header names each unit */}
            <dd className="mono rule__condition">{conditionText(rule.condition, new Map())}</dd>
          </div>
          <div>
            <dt>Action</dt>
            <dd>
              {decision === null ? (
                <span className="mono">{actionText(rule)}</span>
              ) : (
                <>
                  <DecisionTag status={decision} action />{' '}
                  <span className="muted rule__terminal">
                    {decide?.terminal === false ? 'candidate' : 'terminal'}
                  </span>
                </>
              )}
            </dd>
          </div>
          {reason ? (
            <div>
              <dt>Reason for the applicant</dt>
              <dd className="rule__reason" {...contentAttributes(language)}>
                {reason}
              </dd>
            </div>
          ) : null}
        </dl>
        <dl className="kv kv--2">
          <div>
            <dt>Priority</dt>
            <dd className="tabular">
              {rule.priority} · {band.name}
            </dd>
          </div>
          <div>
            <dt>Source</dt>
            <dd>
              {provenance.kind === 'quoted' ? (
                <Actor kind="model">
                  {provenance.confidence === undefined
                    ? 'model'
                    : `model · confidence ${provenance.confidence.toFixed(2)}`}
                </Actor>
              ) : provenance.kind === 'analyst' ? (
                <Actor kind="person">analyst</Actor>
              ) : (
                <span className="vstatus vstatus--pending">Pending</span>
              )}
            </dd>
          </div>
          {decided ? (
            <div>
              <dt>Decided in the last run</dt>
              <dd className="tabular">
                {decided.count} of {decided.decisions} cases
              </dd>
            </div>
          ) : null}
          {since ? (
            <div>
              <dt>Since</dt>
              <dd>{since}</dd>
            </div>
          ) : null}
        </dl>
        {editable && onToggleEnabled ? (
          <div className="field">
            <label className="check" htmlFor={enabledId}>
              <input
                id={enabledId}
                type="checkbox"
                checked={rule.enabled !== false}
                onChange={(event) => onToggleEnabled(event.target.checked)}
                aria-describedby={`${enabledId}-hint`}
              />
              Enabled
            </label>
            <p className="field__hint" id={`${enabledId}-hint`}>
              This rule runs; unchecked, the engine skips it and the table dims it
            </p>
          </div>
        ) : null}
      </section>

      {provenance.kind === 'quoted' && paragraph ? (
        <section className="margin__section" aria-label="Policy">
          <div className="margin__title">
            <span>
              Policy{' '}
              <Chip kind="para" active>
                {paragraph.index}
              </Chip>
            </span>
            <Button variant="link" onClick={() => onShowParagraph(paragraph.index)}>
              Open the policy
            </Button>
          </div>
          <Paragraph
            index={paragraph.index}
            text={paragraph.text}
            language={language}
            quote={provenance.quote}
            cited
            cites={citedBy.map((ruleId) => (
              <Chip
                key={ruleId}
                active={ruleId === rule.id}
                onClick={ruleId === rule.id ? undefined : () => onSelectRule(ruleId)}
              >
                {ruleId}
              </Chip>
            ))}
          />
        </section>
      ) : provenance.kind === 'analyst' ? (
        <section className="margin__section" aria-label="Written by an analyst">
          <div className="margin__title">
            <span>Written by an analyst</span>
            <span className="quiet mono">{provenance.actor}</span>
          </div>
          <div className="he-quote">
            <p {...contentAttributes(language)}>{provenance.note}</p>
          </div>
        </section>
      ) : provenance.kind === 'pending' ? (
        <section className="margin__section" aria-label="Pending a person's approval">
          <div className="margin__title">
            <span>Pending a person&apos;s approval</span>
            <Chip>{provenance.changeRequestId}</Chip>
          </div>
          <div className="he-quote">
            <p {...contentAttributes(language)}>{provenance.rationale}</p>
          </div>
        </section>
      ) : null}

      {reviewFindings.length + findings.length > 0 ? (
        <section className="margin__section" aria-label="Findings on this rule">
          <div className="margin__title">
            <span>Findings on this rule</span>
            {reviewTotal > 0 ? (
              <span className="quiet">
                {reviewFindings.length} of {reviewTotal}
              </span>
            ) : null}
          </div>
          <ul className="review__findings">
            {reviewFindings.map((finding, position) => (
              <FindingItem
                key={finding.id}
                finding={finding}
                language={language}
                editable={editable}
                acknowledging={acknowledging === finding.id}
                onAcknowledge={onAcknowledge}
                onSelectRule={onSelectRule}
                onShowParagraph={onShowParagraph}
                // the way back to the whole review is offered once, under the last finding
                toReview={
                  onOpenReview && position === reviewFindings.length - 1
                    ? { total: reviewTotal, onOpen: onOpenReview }
                    : undefined
                }
              />
            ))}
            {findings.map((finding) => (
              <li key={`${finding.code}${finding.path}`} className="finding">
                <div className="finding__gutter">
                  <span
                    className={`sev sev--${finding.severity === 'error' ? 'error' : 'warning'}`}
                    aria-hidden="true"
                  />
                </div>
                <div className="finding__head">
                  <span className="finding__code">{finding.code}</span>
                </div>
                <p className="rule__finding">{finding.message}</p>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </>
  )
}
