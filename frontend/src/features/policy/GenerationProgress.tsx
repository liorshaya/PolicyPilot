import type { RuleSetDocument, VersionResponse } from '../../api/types'
import type { Generation } from './useGeneration'
import { STAGES, STAGE_LABELS } from './useGeneration'
import { Button } from '../../shared/ui/Button'
import { KIND_LABELS } from '../rules/findings'
import './GenerationProgress.css'

/**
 * Where the generation is, while it runs (Document 2, API Surface: the stream reports parsing, authoring, validating
 * and reviewing). The stages are numbered because they always happen in this order, and the model's own name is
 * never shown as the author of a decision: what it writes becomes a draft a person publishes.
 */
export function GenerationProgress({
  generation,
  onOpenRules,
}: {
  generation: Generation
  onOpenRules: () => void
}) {
  if (!generation.running && generation.draft === null && generation.refusal === null) {
    return null
  }
  return (
    <div className="generation" role="status" aria-live="polite">
      {generation.running ? (
        <ol className="generation__stages">
          {STAGES.map((stage, index) => (
            <li
              key={stage}
              className={`generation__stage${stage === generation.stage ? ' generation__stage--now' : ''}`}
            >
              <span className="generation__number tabular">{index + 1}</span>
              <span>{STAGE_LABELS[stage]}</span>
            </li>
          ))}
        </ol>
      ) : null}

      {generation.draft ? (
        <div className="generation__done">
          <p>
            A draft rule set was written from this policy:{' '}
            <span className="tabular">
              {(generation.draft.ruleSet as RuleSetDocument).rules.length} rules
            </span>
            , version <span className="tabular">{generation.draft.versionNo}</span>. Nothing decides
            cases until a person publishes it.
          </p>
          {(generation.draft.findings ?? []).length > 0 ? (
            // a draft can validate and still be a poor one; whoever approves it has to see what was noted
            <div className="generation__noted">
              <p className="generation__noted-title">
                The validator noted{' '}
                <span className="tabular">{(generation.draft.findings ?? []).length}</span> thing
                {(generation.draft.findings ?? []).length === 1 ? '' : 's'} to check before
                publishing:
              </p>
              <ul className="generation__findings">
                {(generation.draft.findings ?? []).slice(0, 5).map((finding) => (
                  <li key={`${finding.code}${finding.path}`}>
                    <span className="mono">{finding.code}</span>
                    <span>{finding.message}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          <ReviewSummary draft={generation.draft} />
          <Button variant="primary" className="generation__open" onClick={onOpenRules}>
            Review the draft
          </Button>
        </div>
      ) : null}

      {generation.refusal ? (
        <div className="generation__refused" role="alert">
          <p className="generation__refused-title">
            No rule set was written (<span className="mono">{generation.refusal.code}</span>).
            Nothing was stored.
          </p>
          {generation.refusal.findings.length > 0 ? (
            <ul className="generation__findings">
              {generation.refusal.findings.slice(0, 5).map((finding) => (
                <li key={`${finding.code}${finding.path}`}>
                  <span className="mono">{finding.code}</span>
                  <span>{finding.message}</span>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

/**
 * What the reviewer found, before the analyst opens the draft (Document 1, demo step 1: "two rows carry warnings").
 * The findings are the model's reading of the policy; the rule set screen is where each one is acknowledged.
 */
function ReviewSummary({ draft }: { draft: VersionResponse }) {
  const review = draft.review
  if (review === undefined) {
    return null
  }
  if (review.status === 'FAILED') {
    return (
      <p className="generation__noted-title">
        The review could not run; run it again from the rule set before publishing.
      </p>
    )
  }
  if (review.findings.length === 0) {
    return <p className="generation__noted-title">The reviewer found nothing to check.</p>
  }
  return (
    <div className="generation__noted">
      <p className="generation__noted-title">
        The reviewer found <span className="tabular">{review.findings.length}</span> thing
        {review.findings.length === 1 ? '' : 's'} to check against the policy:
      </p>
      <ul className="generation__findings">
        {review.findings.slice(0, 5).map((finding) => (
          <li key={finding.id}>
            <span>{KIND_LABELS[finding.kind]}</span>
            <bdi dir="auto">{finding.message}</bdi>
          </li>
        ))}
      </ul>
    </div>
  )
}
