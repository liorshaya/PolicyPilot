import { ApiError } from '../../api/client'
import type { useExplain } from '../../api/queries'
import type { Audience, Explanation as ExplanationResponse } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { Actor } from '../../shared/ui/Actor'
import { Button } from '../../shared/ui/Button'
import { Chip } from '../../shared/ui/Chip'
import { ErrorState } from '../../shared/ui/States'
import './ExplainPanel.css'

/** The one request both halves of the explanation read: asked for in the trace's head, shown under it. */
export type Explain = ReturnType<typeof useExplain>

/** Who the explanation is written for (Document 4, Prompt 3: officer and applicant). */
const AUDIENCES: { audience: Audience; label: string; reader: string }[] = [
  { audience: 'officer', label: 'Explain for an officer', reader: 'For an officer' },
  { audience: 'applicant', label: 'Explain for the applicant', reader: 'For the applicant' },
]

/** What a model wrote about a trace (the spec's glossary, section 01). */
const WRITTEN_BY =
  'Written by a model from this trace alone; every rule and paragraph it cites was checked against the trace.'

/**
 * The two audiences as buttons in the trace's head (the spec, section 09): an explanation is asked for, never shown by
 * default, and the audience asked for stays pressed.
 */
export function ExplainActions({ explain }: { explain: Explain }) {
  return (
    <>
      {AUDIENCES.map(({ audience, label }) => (
        <Button
          key={audience}
          size="sm"
          aria-pressed={explain.variables === audience}
          busy={explain.isPending && explain.variables === audience}
          onClick={() => explain.mutate(audience)}
        >
          {label}
        </Button>
      ))}
    </>
  )
}

interface ExplanationProps {
  explain: Explain
  /** The language of the policy, which the explanation is written in. */
  language: ContentLanguage
  onOpenRule?: (ruleId: string) => void
}

/**
 * A model's reading of one decision's trace (Brief FR-11; Document 4, Prompt 3; the spec, section 09), under the trace's
 * head: the model's mark and the fixed sentence, then the summary and the named parts, each statement with the rules and
 * paragraphs it cites after it. Every citation was checked against the trace before it reached this screen; the
 * decision and its trace below are the engine's.
 */
export function Explanation({ explain, language, onOpenRule }: ExplanationProps) {
  if (explain.error) {
    return (
      <ErrorState
        code={explain.error instanceof ApiError ? explain.error.code : undefined}
        description="No explanation was written; the trace below is the whole of the decision."
        onRetry={() => explain.mutate(explain.variables ?? 'officer')}
      />
    )
  }
  const explanation = explain.data
  if (!explanation) {
    return null
  }
  const reader = AUDIENCES.find(({ audience }) => audience === explanation.audience)?.reader
  const text = contentAttributes(language)
  const ruleChip = (ruleId: string) => (
    <Chip onClick={onOpenRule ? () => onOpenRule(ruleId) : undefined}>{ruleId}</Chip>
  )
  return (
    <section className="explain" aria-label="Explanation">
      <div className="explain__prov">
        <Actor kind="model" />
        <span>{`${reader ?? ''} · ${WRITTEN_BY}`}</span>
      </div>
      <p className="explain__text" {...text}>
        {explanation.summary}
      </p>
      {explanation.factors.length > 0 ? (
        <>
          <div className="explain__h">Factors</div>
          {explanation.factors.map((factor) => (
            <p key={factor.ruleId} className="explain__factor" {...text}>
              {factor.statement} {ruleChip(factor.ruleId)}{' '}
              {factor.paragraph === null || factor.paragraph === undefined ? (
                <Actor kind="person" title="Written by an analyst" />
              ) : (
                <Chip kind="para">{factor.paragraph}</Chip>
              )}
            </p>
          ))}
        </>
      ) : null}
      {explanation.conditions.length > 0 ? (
        <>
          <div className="explain__h">A person still checks</div>
          {explanation.conditions.map((condition) => (
            <p key={condition.flagCode} className="explain__factor" {...text}>
              {condition.statement} <Chip kind="field">{condition.flagCode}</Chip>
            </p>
          ))}
        </>
      ) : null}
      {explanation.notApplied.length > 0 ? (
        <>
          <div className="explain__h">Evaluated and not applied</div>
          {explanation.notApplied.map((rule) => (
            <p key={rule.ruleId} className="explain__factor" {...text}>
              {rule.statement} {ruleChip(rule.ruleId)}
            </p>
          ))}
        </>
      ) : null}
      {explanation.audience === 'applicant' ? (
        <Button variant="quiet" size="sm" onClick={() => void copyForTheLetter(explanation)}>
          Copy for the letter
        </Button>
      ) : null}
    </section>
  )
}

/**
 * The applicant's explanation as plain text for the letter the product does not send (the spec, section 09): the
 * summary, then each statement on its own line, without the chips.
 */
async function copyForTheLetter(explanation: ExplanationResponse): Promise<void> {
  const lines = [
    explanation.summary,
    ...explanation.factors.map((factor) => factor.statement),
    ...explanation.conditions.map((condition) => condition.statement),
    ...explanation.notApplied.map((rule) => rule.statement),
  ]
  await navigator.clipboard.writeText(lines.join('\n'))
}
