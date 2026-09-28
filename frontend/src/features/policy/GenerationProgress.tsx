import { useId, useState } from 'react'
import type { Review, RuleSetDocument, VersionResponse } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { durationText } from '../../shared/i18n/time'
import { Button } from '../../shared/ui/Button'
import { Note } from '../../shared/ui/Note'
import { Refusal } from '../../shared/ui/Refusal'
import { findingRows } from '../../shared/ui/refusalRows'
import { FindingBrief } from '../rules/ReviewPanel'
import { generationFailureText } from './failures'
import type { Generation, GenerationRefusal, Stage } from './useGeneration'
import { STAGES, STAGE_LABELS } from './useGeneration'
import './GenerationProgress.css'

const COUNT = new Intl.NumberFormat('en-US')

/** "20 rules", "1 finding": a count with its noun. */
function counted(count: number, noun: string): string {
  return `${COUNT.format(count)} ${noun}${count === 1 ? '' : 's'}`
}

/**
 * What each finished stage produced (the spec, section 10, the Policies screen's Generation; the owner's answer of
 * 2026-09-28 to phase 5's first question): the paragraphs the stream read, then the draft's rules, the validator's
 * findings on it and the review's; a review that could not run has no count.
 */
function countOf(stage: Stage, generation: Generation): string | null {
  const draft = generation.draft
  switch (stage) {
    case 'parsing':
      return generation.paragraphs === null ? null : `${COUNT.format(generation.paragraphs)} ¶`
    case 'authoring':
      return draft ? counted((draft.ruleSet as RuleSetDocument).rules.length, 'rule') : null
    case 'validating':
      return draft ? counted((draft.findings ?? []).length, 'problem') : null
    case 'reviewing':
      return draft?.review?.status === 'DONE'
        ? counted(draft.review.findings.length, 'finding')
        : null
  }
}

/**
 * A stage's state in the list: the one running now, done, or neither, for a stage not reached yet and for the stage a
 * refusal ended. With the draft every stage is done, except a review that could not run.
 */
function stateOf(stage: Stage, generation: Generation): 'now' | 'done' | null {
  if (generation.draft) {
    return stage === 'reviewing' && generation.draft.review?.status !== 'DONE' ? null : 'done'
  }
  const reached = generation.stages.indexOf(stage)
  if (reached < 0) {
    return null
  }
  const last = reached === generation.stages.length - 1
  return !last ? 'done' : generation.running ? 'now' : null
}

/**
 * The generation's four stages in the margin (the spec, section 10; section 08's progress): the one running now, and
 * each finished one with what it produced; once the draft has arrived, the run's time beside the title, measured in
 * this browser.
 */
export function GenerationProgress({ generation }: { generation: Generation }) {
  const titleId = useId()
  if (generation.policyId === null) {
    return null
  }
  return (
    <section className="margin__section" aria-labelledby={titleId}>
      <div className="margin__title">
        <span id={titleId}>Generation</span>{' '}
        {generation.draft && generation.took !== null ? (
          <span className="quiet">done · {durationText(generation.took * 1_000)}</span>
        ) : null}
      </div>
      <div className="progress">
        {STAGES.map((stage) => {
          const state = stateOf(stage, generation)
          const count = state === 'done' ? countOf(stage, generation) : null
          return (
            <div
              key={stage}
              className={`progress__step${state === null ? '' : ` progress__step--${state}`}`}
              aria-current={state === 'now' ? 'step' : undefined}
            >
              <span className="progress__mark" />
              <span>{STAGE_LABELS[stage]}</span>
              {count === null ? null : <span className="progress__meta">{count}</span>}
            </div>
          )
        })}
      </div>
    </section>
  )
}

/**
 * How the run ended, on the sheet under the policy's title (the spec, section 10): the model's draft announced by the
 * dashed note with the model's mark and one button, or the refusal of section 08.
 */
export function GenerationResult({
  generation,
  onReview,
}: {
  generation: Generation
  onReview: () => void
}) {
  if (generation.draft) {
    return (
      <div className="generation__draft">
        <Note tone="proposal">
          <DraftSentence draft={generation.draft} />
        </Note>
        <Button onClick={onReview}>Review the draft</Button>
      </div>
    )
  }
  if (generation.refusal) {
    return <Refused refusal={generation.refusal} />
  }
  return null
}

/** "A draft rule set was written from this policy: 20 rules, version 2. …", the product's sentence (the spec, section 08). */
function DraftSentence({ draft }: { draft: VersionResponse }) {
  return (
    <>
      A draft rule set was written from this policy:{' '}
      <b>{counted((draft.ruleSet as RuleSetDocument).rules.length, 'rule')}</b>
      {`, version ${String(draft.versionNo)}. Nothing decides cases until a person publishes it.`}
    </>
  )
}

/**
 * The refusal of section 08: the code, a row per pointer into the model's answer and its problem, and the closing fact;
 * what the model proposed stays behind a link, as the change screen keeps it (Document 5, RT-04).
 */
function Refused({ refusal }: { refusal: GenerationRefusal }) {
  const [shown, setShown] = useState(false)
  const proposed = refusal.document !== null && refusal.document !== undefined
  return (
    <div className="generation__refused">
      <Refusal
        code={refusal.code}
        title="No rule set was written."
        rows={findingRows(refusal.findings)}
        explanation={generationFailureText(refusal)}
        next={
          proposed ? (
            <Button variant="link" aria-expanded={shown} onClick={() => setShown(!shown)}>
              What the model proposed
            </Button>
          ) : undefined
        }
      />
      {shown ? (
        <pre className="generation__proposed mono" aria-label="What the model proposed">
          {JSON.stringify(refusal.document, null, 2)}
        </pre>
      ) : null}
    </div>
  )
}

/**
 * The review, summed up in the margin before the analyst opens the draft (the spec, section 10): what the reviewer
 * found and how much of it blocks publishing, the first three findings, and the way to all of them in the review, where
 * each is acknowledged. The findings are the model's reading of the policy; nothing here decides anything.
 */
export function ReviewSummary({
  review,
  language,
  onOpen,
}: {
  review: Review | undefined
  language: ContentLanguage
  onOpen: () => void
}) {
  const titleId = useId()
  if (review === undefined) {
    return null
  }
  if (review.status === 'FAILED') {
    return (
      <section className="margin__section" aria-labelledby={titleId}>
        <div className="margin__title">
          <span id={titleId}>Review</span>
        </div>
        <Note tone="warning">
          The review could not run; run it again from the rule set before publishing.
        </Note>
      </section>
    )
  }
  const total = review.findings.length
  const blocking = review.findings.filter((finding) => finding.blocking).length
  return (
    <section className="margin__section" aria-labelledby={titleId}>
      <div className="margin__title">
        <span id={titleId}>
          {total === 0
            ? 'The reviewer found nothing to check'
            : `The reviewer found ${counted(total, 'thing')} to check`}
        </span>{' '}
        {blocking > 0 ? (
          <span className="quiet">{`${COUNT.format(blocking)} block${blocking === 1 ? 's' : ''} publishing`}</span>
        ) : null}
      </div>
      {review.findings.slice(0, 3).map((finding) => (
        <FindingBrief key={finding.id} finding={finding} language={language} />
      ))}
      {total > 0 ? (
        <Button variant="link" className="generation__all" onClick={onOpen}>
          All {total}, in the review
        </Button>
      ) : null}
    </section>
  )
}
