import { useState } from 'react'
import type { GapResolution, Review, ReviewFinding } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { Button } from '../../shared/ui/Button'
import { KIND_LABELS, needs, publishBlockers, RESOLUTION_LABELS } from './findings'
import './ReviewPanel.css'

interface ReviewPanelProps {
  review: Review | undefined
  /** The language of the policy, which the reviewer's messages are written in. */
  language: ContentLanguage
  /** Only a draft is reviewed and acknowledged; a published version shows the review it was published with. */
  draft: boolean
  running: boolean
  onRunReview: () => void
  acknowledging: string | null
  onAcknowledge: (findingId: string, resolution?: GapResolution, note?: string) => void
  onSelectRule: (ruleId: string) => void
  onShowParagraph: (index: number) => void
}

/**
 * The reviewer's findings on a draft (Brief FR-5; Document 2, Flow 1): what the model noticed that a program cannot,
 * each anchored to rules and paragraphs. The model only points; the analyst decides, and every acknowledgement the
 * API records says what was decided, so the meaning of a published version can be traced back to a person.
 */
export function ReviewPanel({
  review,
  language,
  draft,
  running,
  onRunReview,
  acknowledging,
  onAcknowledge,
  onSelectRule,
  onShowParagraph,
}: ReviewPanelProps) {
  const blockers = draft ? publishBlockers(review) : []
  const rerun = draft && review?.status !== 'DONE'
  return (
    <section className="review" aria-label="Review of the draft">
      <div className="review__head">
        <p className="review__summary">
          {review === undefined
            ? 'Not reviewed yet.'
            : review.status === 'FAILED'
              ? 'The review could not run.'
              : `The reviewer noted ${String(review.findings.length)} thing${
                  review.findings.length === 1 ? '' : 's'
                } to check against the policy${review.status === 'STALE' ? ', before the last edit' : ''}.`}
        </p>
        {rerun ? (
          <Button variant="secondary" loading={running} onClick={onRunReview}>
            {review === undefined ? 'Review the draft' : 'Run the review again'}
          </Button>
        ) : null}
      </div>
      {blockers.length > 0 ? (
        <p className="review__blockers" role="note">
          Publishing waits: {blockers.join(' ')}
        </p>
      ) : null}
      {review && review.findings.length > 0 ? (
        <ul className="review__findings">
          {review.findings.map((finding) => (
            <FindingItem
              key={finding.id}
              finding={finding}
              language={language}
              editable={draft && review.status !== 'FAILED'}
              acknowledging={acknowledging === finding.id}
              onAcknowledge={onAcknowledge}
              onSelectRule={onSelectRule}
              onShowParagraph={onShowParagraph}
            />
          ))}
        </ul>
      ) : null}
    </section>
  )
}

function FindingItem({
  finding,
  language,
  editable,
  acknowledging,
  onAcknowledge,
  onSelectRule,
  onShowParagraph,
}: {
  finding: ReviewFinding
  language: ContentLanguage
  editable: boolean
  acknowledging: boolean
  onAcknowledge: ReviewPanelProps['onAcknowledge']
  onSelectRule: (ruleId: string) => void
  onShowParagraph: (index: number) => void
}) {
  const [open, setOpen] = useState(false)
  const [resolution, setResolution] = useState<GapResolution | ''>('')
  const [note, setNote] = useState('')
  const need = needs(finding)
  const ready =
    need === 'resolution' ? resolution !== '' : need === 'note' ? note.trim() !== '' : true
  const acknowledgement = finding.acknowledgement

  return (
    <li
      className={`review__finding review__finding--${finding.severity}${
        acknowledgement ? ' review__finding--done' : ''
      }`}
    >
      <div className="review__finding-head">
        <span className="review__id mono">{finding.id}</span>
        <span className={`review__kind review__kind--${finding.kind}`}>
          {KIND_LABELS[finding.kind]}
        </span>
        {finding.blocking ? <span className="review__blocking">Blocks publishing</span> : null}
        <span className="review__anchors">
          {finding.ruleIds.map((ruleId) => (
            <button
              key={ruleId}
              type="button"
              className="review__anchor mono"
              onClick={() => onSelectRule(ruleId)}
            >
              {ruleId}
            </button>
          ))}
          {finding.paragraphIndexes.map((index) => (
            <button
              key={index}
              type="button"
              className="review__anchor tabular"
              aria-label={`Paragraph ${String(index)}`}
              onClick={() => onShowParagraph(index)}
            >
              ¶ {index}
            </button>
          ))}
        </span>
      </div>
      <p className="review__message" {...contentAttributes(language)}>
        {finding.message}
      </p>
      <p className="review__suggestion" {...contentAttributes(language)}>
        {finding.suggestion}
      </p>

      {acknowledgement ? (
        <p className="review__acknowledged">
          Acknowledged
          {acknowledgement.resolution ? `: ${RESOLUTION_LABELS[acknowledgement.resolution]}` : ''}
          {acknowledgement.note ? (
            <>
              {' · '}
              <bdi dir="auto">{acknowledgement.note}</bdi>
            </>
          ) : null}
        </p>
      ) : editable && !open ? (
        <Button variant="secondary" onClick={() => setOpen(true)}>
          Acknowledge
        </Button>
      ) : null}

      {editable && open && !acknowledgement ? (
        <form
          className="review__form"
          onSubmit={(event) => {
            event.preventDefault()
            onAcknowledge(
              finding.id,
              resolution === '' ? undefined : resolution,
              note.trim() === '' ? undefined : note.trim(),
            )
          }}
        >
          {need === 'resolution' ? (
            <fieldset className="review__resolutions">
              <legend>How is the gap resolved?</legend>
              {(Object.keys(RESOLUTION_LABELS) as GapResolution[]).map((option) => (
                <label key={option} className="review__resolution">
                  <input
                    type="radio"
                    name={`resolution-${finding.id}`}
                    value={option}
                    checked={resolution === option}
                    onChange={() => setResolution(option)}
                  />
                  {RESOLUTION_LABELS[option]}
                </label>
              ))}
            </fieldset>
          ) : null}
          <label className="review__note">
            <span>
              {need === 'note' ? 'Why the draft stands as it is (required)' : 'Note (optional)'}
            </span>
            <textarea
              value={note}
              rows={2}
              maxLength={2000}
              onChange={(e) => setNote(e.target.value)}
            />
          </label>
          <div className="review__form-actions">
            <Button variant="primary" type="submit" disabled={!ready} loading={acknowledging}>
              Record the acknowledgement
            </Button>
            <Button variant="ghost" type="button" onClick={() => setOpen(false)}>
              Cancel
            </Button>
          </div>
        </form>
      ) : null}
    </li>
  )
}
