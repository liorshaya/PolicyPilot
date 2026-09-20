import type { RuleSetDocument } from '../../api/types'
import type { Generation } from './useGeneration'
import { STAGES, STAGE_LABELS } from './useGeneration'
import { Button } from '../../shared/ui/Button'
import './GenerationProgress.css'

/**
 * Where the generation is, while it runs (Document 2, API Surface: the stream reports parsing, authoring and
 * validating). The stages are numbered because they always happen in this order, and the model's own name is
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
          <Button variant="primary" onClick={onOpenRules}>
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
