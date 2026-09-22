import { ApiError } from '../../api/client'
import { useExplain } from '../../api/queries'
import type { Audience } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { Button } from '../../shared/ui/Button'
import { ErrorState } from '../../shared/ui/States'
import './ExplainPanel.css'

interface ExplainPanelProps {
  decisionId: string
  language: ContentLanguage
  onOpenRule?: (ruleId: string) => void
}

/** Who the explanation is written for (Document 4, Prompt 3: officer and applicant). */
const AUDIENCES: { audience: Audience; label: string }[] = [
  { audience: 'officer', label: 'Explain for an officer' },
  { audience: 'applicant', label: 'Explain for the applicant' },
]

/**
 * A model's reading of one decision's trace (Brief FR-11; Document 4, Prompt 3). It is asked for, never shown by
 * default, and said to be the model's: the decision and its trace below are the engine's, and every rule and paragraph
 * the explanation cites was checked against that trace before it reached this screen.
 */
export function ExplainPanel({ decisionId, language, onOpenRule }: ExplainPanelProps) {
  const explain = useExplain(decisionId)
  const explanation = explain.data

  return (
    <section className="explain" aria-label="Explanation">
      <div className="explain__actions">
        {AUDIENCES.map(({ audience, label }) => (
          <Button
            key={audience}
            variant={explanation?.audience === audience ? 'primary' : 'secondary'}
            loading={explain.isPending && explain.variables === audience}
            onClick={() => explain.mutate(audience)}
          >
            {label}
          </Button>
        ))}
      </div>

      {explain.error ? (
        <ErrorState
          code={explain.error instanceof ApiError ? explain.error.code : undefined}
          description="No explanation was written; the trace below is the whole of the decision."
          onRetry={() => explain.mutate(explain.variables ?? 'officer')}
        />
      ) : null}

      {explanation ? (
        <div className="explain__body">
          <p className="explain__provenance">
            Written by a model from this trace alone; every rule and paragraph it cites was checked
            against the trace.
          </p>
          <p className="explain__summary" {...contentAttributes(language)}>
            {explanation.summary}
          </p>
          {explanation.factors.length > 0 ? (
            <ol className="explain__factors">
              {explanation.factors.map((factor) => (
                <li key={factor.ruleId} className="explain__factor">
                  <span className="explain__cites">
                    {onOpenRule ? (
                      <button
                        type="button"
                        className="explain__cite mono"
                        onClick={() => onOpenRule(factor.ruleId)}
                      >
                        {factor.ruleId}
                      </button>
                    ) : (
                      <span className="explain__cite mono">{factor.ruleId}</span>
                    )}
                    {factor.paragraph === null || factor.paragraph === undefined ? (
                      <span className="explain__cite">Added by an analyst</span>
                    ) : (
                      <span className="explain__cite tabular">¶ {factor.paragraph}</span>
                    )}
                  </span>
                  <span {...contentAttributes(language)}>{factor.statement}</span>
                </li>
              ))}
            </ol>
          ) : null}
          {explanation.conditions.length > 0 ? (
            <>
              <h3 className="explain__heading">A person still checks</h3>
              <ul className="explain__list">
                {explanation.conditions.map((condition) => (
                  <li key={condition.flagCode}>
                    <span className="explain__cite mono">{condition.flagCode}</span>
                    <span {...contentAttributes(language)}>{condition.statement}</span>
                  </li>
                ))}
              </ul>
            </>
          ) : null}
          {explanation.notApplied.length > 0 ? (
            <>
              <h3 className="explain__heading">Evaluated and not applied</h3>
              <ul className="explain__list">
                {explanation.notApplied.map((rule) => (
                  <li key={rule.ruleId}>
                    <span className="explain__cite mono">{rule.ruleId}</span>
                    <span {...contentAttributes(language)}>{rule.statement}</span>
                  </li>
                ))}
              </ul>
            </>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}
