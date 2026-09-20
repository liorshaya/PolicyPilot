import type { Finding, Rule } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { Panel } from '../../shared/ui/Panel'
import { DecisionTag } from '../../shared/ui/StatusTag'
import { actionText, decisionOf } from './tableModel'
import './RuleDrawer.css'

interface RuleDrawerProps {
  rule: Rule
  language: ContentLanguage
  /** The paragraph the rule cites, when the policy is open beside it. */
  paragraph?: { index: number; text: string }
  /** The findings anchored to this rule (Document 3, Error reporting shape). */
  findings: Finding[]
  onClose: () => void
}

/**
 * One rule in full: what it compares, what it does, and where it comes from (Document 3, Provenance). The source
 * paragraph is shown beside the rule, because that pairing is what makes a rule auditable.
 */
export function RuleDrawer({ rule, language, paragraph, findings, onClose }: RuleDrawerProps) {
  const decision = decisionOf(rule)
  return (
    <Panel
      fill
      title={<span className="mono">{rule.id}</span>}
      subtitle={<bdi dir="auto">{rule.label}</bdi>}
      actions={
        <button type="button" className="button button--ghost" onClick={onClose}>
          <span>Close</span>
        </button>
      }
    >
      <dl className="drawer">
        <dt>Priority</dt>
        <dd className="tabular">
          {rule.priority}
          {rule.enabled === false ? ' · disabled' : ''}
        </dd>

        <dt>Action</dt>
        <dd>{decision === null ? actionText(rule) : <DecisionTag status={decision} />}</dd>

        {rule.actions
          .filter((action) => action.reason !== undefined)
          .map((action) => (
            <div className="drawer__pair" key={action.reason}>
              <dt>Reason given to the applicant</dt>
              <dd>
                <bdi {...contentAttributes(language)}>{action.reason}</bdi>
              </dd>
            </div>
          ))}

        <dt>Source</dt>
        <dd>
          {rule.provenance.kind === 'quoted' ? (
            <div className="drawer__source">
              <p className="drawer__paragraph">
                Paragraph <span className="mono">{rule.provenance.paragraph}</span>
                {rule.provenance.confidence !== undefined ? (
                  <>
                    {' · model confidence '}
                    <span className="tabular">{rule.provenance.confidence.toFixed(2)}</span>
                  </>
                ) : null}
              </p>
              <blockquote className="drawer__quote" {...contentAttributes(language)}>
                {rule.provenance.quote}
              </blockquote>
              {paragraph ? (
                <p className="drawer__full" {...contentAttributes(language)}>
                  {paragraph.text}
                </p>
              ) : null}
            </div>
          ) : rule.provenance.kind === 'analyst' ? (
            <div className="drawer__source">
              <p className="drawer__paragraph">Written by {rule.provenance.actor}</p>
              <blockquote className="drawer__quote" dir="auto">
                {rule.provenance.note}
              </blockquote>
            </div>
          ) : (
            <div className="drawer__source">
              <p className="drawer__paragraph">
                Pending a person's approval · change {rule.provenance.changeRequestId}
              </p>
              <blockquote className="drawer__quote" dir="auto">
                {rule.provenance.rationale}
              </blockquote>
            </div>
          )}
        </dd>

        {findings.length > 0 ? (
          <div className="drawer__pair">
            <dt>Findings</dt>
            <dd>
              <ul className="drawer__findings">
                {findings.map((finding) => (
                  <li
                    key={`${finding.code}${finding.path}`}
                    className={`drawer__finding drawer__finding--${finding.severity}`}
                  >
                    <span className="mono">{finding.code}</span>
                    <span>{finding.message}</span>
                  </li>
                ))}
              </ul>
            </dd>
          </div>
        ) : null}

        <dt>Rule JSON</dt>
        <dd>
          <pre className="drawer__json mono">{JSON.stringify(rule, null, 2)}</pre>
        </dd>
      </dl>
    </Panel>
  )
}
