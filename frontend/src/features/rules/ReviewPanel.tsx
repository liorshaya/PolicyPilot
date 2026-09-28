import { useId, useState } from 'react'
import type { Finding, GapResolution, Review, ReviewFinding } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { dateTimeOf, timeOf } from '../../shared/i18n/time'
import { Actor, PERSON } from '../../shared/ui/Actor'
import { Button } from '../../shared/ui/Button'
import { Chip } from '../../shared/ui/Chip'
import { Icon } from '../../shared/ui/Icon'
import { Seal } from '../../shared/ui/Seal'
import { useInView } from '../../shared/ui/useInView'
import '../../shared/ui/Severity.css'
import { KIND_MARKS } from '../../shared/ui/findingKinds'
import {
  KIND_LABELS,
  needs,
  publishBlockers,
  publishGates,
  RESOLUTION_LABELS,
  reviewCounts,
  reviewStatusLine,
} from './findings'
import './ReviewPanel.css'

/** The note an acknowledgement records follows the chat message limit (Document 2, the acknowledge route). */
const NOTE_LIMIT = 2000
const COUNT = new Intl.NumberFormat('en-US')

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
  /** The finding the palette opened: the current one, brought into view (the spec, section 08). */
  current?: string | null
}

/**
 * The reviewer's findings on a draft (Brief FR-5; Document 2, Flow 1; the spec, section 09, "The review"): the
 * lifecycle first, then each finding as a claim in the reviewer's words, its evidence and what to do. The model only
 * points; the analyst decides, and every acknowledgement the API records says what was decided, so the meaning of a
 * published version can be traced back to a person.
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
  current = null,
}: ReviewPanelProps) {
  const titleId = useId()
  const counts = review ? reviewCounts(review) : null
  return (
    <section className="review" aria-label="Review of the draft">
      <div className="sec">
        <h2 className="sec__title" aria-labelledby={titleId}>
          <span id={titleId}>Review</span>
          {counts ? <span className="quiet">{countsText(counts)}</span> : null}
        </h2>
        <div className="sec__side">
          <Actor kind="model">reviewer · model</Actor>
          {draft ? (
            <Button size="sm" busy={running} onClick={onRunReview}>
              {review === undefined ? 'Review the draft' : 'Run the review again'}
            </Button>
          ) : null}
        </div>
      </div>
      <div className="review__status">
        <Actor kind="system" />
        <span>{reviewStatusLine(review)}</span>
      </div>
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
              current={finding.id === current}
            />
          ))}
        </ul>
      ) : null}
    </section>
  )
}

/** "· 10 findings · 7 block publishing · 1 acknowledged": a count that is zero says nothing and is left out. */
function countsText({ findings, blocking, acknowledged }: ReturnType<typeof reviewCounts>): string {
  return [
    `${String(findings)} finding${findings === 1 ? '' : 's'}`,
    blocking > 0 ? `${String(blocking)} block${blocking === 1 ? 's' : ''} publishing` : null,
    acknowledged > 0 ? `${String(acknowledged)} acknowledged` : null,
  ]
    .filter((part) => part !== null)
    .map((part) => ` · ${part}`)
    .join('')
}

interface FindingItemProps {
  finding: ReviewFinding
  language: ContentLanguage
  /** A finding of a draft can be acknowledged; one of a published version is read. */
  editable: boolean
  acknowledging: boolean
  onAcknowledge: ReviewPanelProps['onAcknowledge']
  onSelectRule: (ruleId: string) => void
  onShowParagraph: (index: number) => void
  /** Under a rule in the margin, the finding leads back to the whole review. */
  toReview?: { total: number; onOpen: () => void }
  /** The one a deep link opened: marked current, brought into view, and flashed once (the spec, section 02). */
  current?: boolean
}

/**
 * One finding (the spec, section 09): its code and kind, "Blocks publishing" as an ink outline when it blocks, the claim
 * in the reviewer's language, the evidence as chips on their own line, and what to do. Acknowledging opens the paper box
 * in place; an acknowledged finding is the inline seal, with the resolution and the note under it.
 */
export function FindingItem({
  finding,
  language,
  editable,
  acknowledging,
  onAcknowledge,
  onSelectRule,
  onShowParagraph,
  toReview,
  current = false,
}: FindingItemProps) {
  const [open, setOpen] = useState(false)
  const ref = useInView<HTMLLIElement>(current)
  const acknowledgement = finding.acknowledgement
  return (
    <li
      ref={ref}
      className={['finding', acknowledgement ? 'finding--done' : '', current ? 'flash' : '']
        .filter(Boolean)
        .join(' ')}
      aria-current={current ? 'true' : undefined}
    >
      <div className="finding__gutter">
        {/* the mark is drawn for the eye; its word stands beside it, in the head */}
        <span className={`sev sev--${KIND_MARKS[finding.kind]}`} aria-hidden="true" />
      </div>
      <div className="finding__head">
        <span className="finding__code">{finding.id}</span>
        <span className="finding__kind">{KIND_LABELS[finding.kind]}</span>
        {finding.blocking ? <span className="tag tag--ink">Blocks publishing</span> : null}
      </div>
      <p className="finding__claim" {...contentAttributes(language)}>
        {finding.message}
      </p>
      {finding.ruleIds.length + finding.paragraphIndexes.length > 0 ? (
        <div className="finding__evidence">
          <span className="label">Evidence</span>
          {finding.ruleIds.map((ruleId) => (
            <Chip key={ruleId} onClick={() => onSelectRule(ruleId)}>
              {ruleId}
            </Chip>
          ))}
          {finding.paragraphIndexes.map((index) => (
            <Chip
              key={index}
              kind="para"
              label={`Paragraph ${String(index)}`}
              onClick={() => onShowParagraph(index)}
            >
              {index}
            </Chip>
          ))}
        </div>
      ) : null}
      {finding.suggestion ? (
        <p className="finding__todo" {...contentAttributes(language)}>
          <span className="label" lang="en" dir="ltr">
            What to do
          </span>
          {finding.suggestion}
        </p>
      ) : null}
      {acknowledgement ? (
        <div className="finding__done">
          <Seal kicker="Acknowledged" line={`${timeOf(acknowledgement.at)} · ${PERSON}`} inline />
          {acknowledgement.resolution ? (
            <p className="muted">{RESOLUTION_LABELS[acknowledgement.resolution]}</p>
          ) : null}
          {acknowledgement.note ? (
            <div className="he-quote">
              <p {...contentAttributes(language)}>{acknowledgement.note}</p>
            </div>
          ) : null}
        </div>
      ) : editable && open ? (
        <AcknowledgementBox
          finding={finding}
          acknowledging={acknowledging}
          onAcknowledge={onAcknowledge}
          onCancel={() => setOpen(false)}
        />
      ) : editable || toReview ? (
        <div className="finding__actions">
          {editable ? (
            <Button size="sm" onClick={() => setOpen(true)}>
              Acknowledge
            </Button>
          ) : null}
          {toReview ? (
            <Button variant="link" onClick={toReview.onOpen}>
              All {toReview.total}, in the review
            </Button>
          ) : null}
        </div>
      ) : null}
    </li>
  )
}

/**
 * A finding in brief, as the Policies screen's margin sums up a review (the spec, section 10): its mark, its code and
 * kind, and the claim in the reviewer's language; the evidence and what to do wait in the review itself.
 */
export function FindingBrief({
  finding,
  language,
}: {
  finding: ReviewFinding
  language: ContentLanguage
}) {
  return (
    <div className="finding">
      <div className="finding__gutter">
        {/* the mark is drawn for the eye; its word stands beside it, in the head */}
        <span className={`sev sev--${KIND_MARKS[finding.kind]}`} aria-hidden="true" />
      </div>
      <div className="finding__head">
        <span className="finding__code">{finding.id}</span>
        <span className="finding__kind">{KIND_LABELS[finding.kind]}</span>
      </div>
      <p className="finding__claim" {...contentAttributes(language)}>
        {finding.message}
      </p>
    </div>
  )
}

/**
 * The paper box that records an acknowledgement (the spec, section 09): an error needs the reason the draft stands, a
 * gap one of the three resolutions and takes a note, and a warning nothing; the button says what happens, an audit entry
 * with the analyst's name.
 */
function AcknowledgementBox({
  finding,
  acknowledging,
  onAcknowledge,
  onCancel,
}: {
  finding: ReviewFinding
  acknowledging: boolean
  onAcknowledge: ReviewPanelProps['onAcknowledge']
  onCancel: () => void
}) {
  const noteId = useId()
  const [resolution, setResolution] = useState<GapResolution | ''>('')
  const [note, setNote] = useState('')
  const need = needs(finding)
  const ready =
    need === 'resolution' ? resolution !== '' : need === 'note' ? note.trim() !== '' : true
  return (
    <form
      className="finding__ack"
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
        <fieldset>
          <legend>How is the gap resolved?</legend>
          {(Object.keys(RESOLUTION_LABELS) as GapResolution[]).map((option) => (
            <label key={option} className="check">
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
      {need === 'nothing' ? null : (
        <>
          <label className="legend-t" htmlFor={noteId}>
            {need === 'note' ? 'Why the draft stands as it is (required)' : 'Note (optional)'}
          </label>
          <textarea
            id={noteId}
            className="textarea textarea--he"
            dir="auto"
            value={note}
            maxLength={NOTE_LIMIT}
            onChange={(event) => setNote(event.target.value)}
          />
        </>
      )}
      <div className="finding__ack-actions">
        <Button variant="primary" size="sm" type="submit" disabled={!ready} busy={acknowledging}>
          Record the acknowledgement
        </Button>
        <Button variant="quiet" size="sm" onClick={onCancel}>
          Cancel
        </Button>
        {need === 'nothing' ? null : (
          <span className={note.length >= 0.9 * NOTE_LIMIT ? 'counter counter--near' : 'counter'}>
            {`${COUNT.format(note.length)} / ${COUNT.format(NOTE_LIMIT)}`}
          </span>
        )}
      </div>
    </form>
  )
}

interface PublishBoxProps {
  version: { status: string; versionNo: number; publishedAt?: string }
  review: Review | undefined
  /** The validator's findings on the version (Document 3, Static Validation). */
  findings: Finding[]
  publishing: boolean
  onPublish: () => void
  running: boolean
  onRunReview: () => void
  /** The version was published a moment ago: its seal lands with the one signature motion. */
  justPublished?: boolean
}

/**
 * The publish box (the spec, section 09): the real gates with the fact behind each, the product's own reason while
 * publishing waits, and the button, which never hides; a stale review offers to run it again in its place. A published
 * version is the seal of the person who published it.
 */
export function PublishBox({
  version,
  review,
  findings,
  publishing,
  onPublish,
  running,
  onRunReview,
  justPublished = false,
}: PublishBoxProps) {
  const number = version.versionNo
  if (version.status === 'PUBLISHED') {
    return (
      <div className="publish-box">
        <div className="publish-box__row publish-box__row--head">Published</div>
        <div className="publish-box__row publish-box__row--sealed">
          <Seal
            kicker="Published"
            line={`v${String(number)}${version.publishedAt ? ` · ${dateTimeOf(version.publishedAt)}` : ''}`}
            by={PERSON}
            stamp={justPublished}
          />
          <span className="publish-box__reason">
            Version {number} decides cases from now on.
            {number > 1 ? (
              <>
                {' '}
                <br />
                Version {number - 1} stays readable.
              </>
            ) : null}
          </span>
        </div>
      </div>
    )
  }
  const gates = publishGates(version, review, findings)
  const problems = findings.filter((finding) => finding.severity === 'error').length
  const reasons =
    version.status === 'DRAFT'
      ? [
          ...(problems > 0
            ? [`The validator found ${String(problems)} problem${problems === 1 ? '' : 's'}.`]
            : []),
          ...publishBlockers(review),
        ]
      : ['Only a draft is published.']
  return (
    <div className="publish-box">
      <div className="publish-box__row publish-box__row--head">Publishing version {number}</div>
      {gates.map((gate) => (
        <div key={gate.label} className="publish-box__row">
          <span className={`publish-box__check publish-box__check--${gate.state}`}>
            {gate.state === 'wait' ? null : <Icon name={gate.state === 'ok' ? 'check' : 'cross'} />}
          </span>
          <span>{gate.label}</span>
          {gate.fact ? <span className="publish-box__reason">{gate.fact}</span> : null}
        </div>
      ))}
      <div className="publish-box__row publish-box__row--action">
        <span className="publish-box__reason">
          {reasons.length > 0 ? unbroken(`Publishing waits: ${reasons.join(' ')}`) : null}
        </span>
        {review?.status === 'STALE' ? (
          <Button busy={running} onClick={onRunReview}>
            Run the review again
          </Button>
        ) : (
          <Button
            variant="primary"
            busy={publishing}
            disabled={gates.some((gate) => gate.state !== 'ok')}
            onClick={onPublish}
          >
            Publish version {number}
          </Button>
        )}
      </div>
    </div>
  )
}

/** A sentence with each finding's id kept on one line: "F-" and "2" never part at the end of a line. */
function unbroken(sentence: string) {
  return sentence.split(/(F-\d+)/).map((part) =>
    /^F-\d+$/.test(part) ? (
      <span key={part} className="publish-box__id">
        {part}
      </span>
    ) : (
      part
    ),
  )
}
