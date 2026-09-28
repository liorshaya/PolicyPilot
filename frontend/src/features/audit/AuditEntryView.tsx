import type { ReactNode } from 'react'
import type { AuditEntry, Diff, FieldSchema, FindingKind } from '../../api/types'
import { directionOfText, isolated, type ContentLanguage } from '../../shared/i18n/direction'
import { dateTimeOf, timeOf } from '../../shared/i18n/time'
import { Actor, PERSON, type ActorKind } from '../../shared/ui/Actor'
import { Chip } from '../../shared/ui/Chip'
import { Seal } from '../../shared/ui/Seal'
import { DECISION_LABELS, type DecisionStatus } from '../../shared/ui/decisionLabels'
import { KIND_LABELS } from '../../shared/ui/findingKinds'
import { useInView } from '../../shared/ui/useInView'
import { DiffView } from '../change/DiffView'
import { RegressionReport } from '../change/RegressionReport'
import { comparisonText, unifiedRows } from '../change/diffRows'
import { changeRequestName } from '../change/names'
import type { Regression } from '../change/types'
import { RESOLUTION_LABELS } from '../rules/findings'

/** Each audit action's verb (the spec, section 09: Published, Change proposed, …, Finding acknowledged, Reset). */
const VERBS: Record<AuditEntry['action'], string> = {
  PUBLISH: 'Published',
  CHANGE_PROPOSED: 'Change proposed',
  CHANGE_APPROVED: 'Change approved',
  CHANGE_REJECTED: 'Change rejected',
  GAP_ACKNOWLEDGED: 'Finding acknowledged',
  RESET: 'Reset',
}

/**
 * Who each action is by (the spec, section 04, and the board's note of 2026-09-28): the sandbox is every entry's actor,
 * so a person's act carries the person's mark, a proposal the model's, and a reset the system's.
 */
const ACTORS: Record<AuditEntry['action'], ActorKind> = {
  PUBLISH: 'person',
  CHANGE_PROPOSED: 'model',
  CHANGE_APPROVED: 'person',
  CHANGE_REJECTED: 'person',
  GAP_ACKNOWLEDGED: 'person',
  RESET: 'system',
}

const COUNT = new Intl.NumberFormat('en-US')

const ACTOR_NAMES: Record<ActorKind, string> = {
  person: PERSON,
  model: 'Model',
  engine: 'Engine',
  system: 'System',
}

interface AuditEntryViewProps {
  entry: AuditEntry
  /** The rule set's language, for the diff an approval stored. */
  language: ContentLanguage
  fields?: FieldSchema[]
  /** The entry the palette opened: current, brought into view and flashed once (the deep link, section 02). */
  opened?: boolean
}

/** What an entry's details hold, read defensively: an entry is stored JSON, written by the action that made it. */
interface Details {
  versionNo?: number
  rules?: number
  warnings?: unknown[]
  forkedFromVersionId?: string
  patches?: number
  /** A proposal's: the rules the model was shown, and what the answers to its prompts spent (since 2026-09-28). */
  candidates?: string[]
  tokens?: number
  requestText?: string | null
  note?: string | null
  diff?: Diff
  regression?: Regression
  id?: string
  kind?: FindingKind
  acknowledgement?: { resolution?: keyof typeof RESOLUTION_LABELS; note?: string | null }
}

/**
 * One row of the audit log (the spec, section 09): the time, the mark of who acted, and a provenance line with the
 * verb, the chips and, for a person's decision on a change, the seal; under it what the entry recorded, a note as a
 * quotation in its own direction, and for an approval the request, the diff and the regression it carried, rendered
 * from the JSON the entry stored and never recomputed (Document 3, Structural diff).
 */
export function AuditEntryView({ entry, language, fields, opened = false }: AuditEntryViewProps) {
  const details = (entry.details ?? {}) as Details
  const actor = ACTORS[entry.action]
  const version = entry.action === 'PUBLISH' || entry.action === 'CHANGE_APPROVED'
  const ref = useInView<HTMLLIElement>(opened)
  return (
    <li
      ref={ref}
      className={['event', version ? 'event--version' : '', opened ? 'flash' : '']
        .filter(Boolean)
        .join(' ')}
      aria-current={opened ? 'true' : undefined}
    >
      <span className="event__time">
        <time dateTime={entry.at} title={dateTimeOf(entry.at)}>
          {timeOf(entry.at)}
        </time>
      </span>
      <span className="event__mark">
        <Actor kind={actor} title={ACTOR_NAMES[actor]} />
      </span>
      <div className="event__body">
        <Recorded entry={entry} details={details} language={language} fields={fields} />
      </div>
    </li>
  )
}

function Recorded({
  entry,
  details,
  language,
  fields,
}: {
  entry: AuditEntry
  details: Details
  language: ContentLanguage
  fields?: FieldSchema[]
}) {
  const verb = <span className="event__verb">{VERBS[entry.action]}</span>
  const request =
    entry.changeRequestNumber === null ? null : (
      <Chip>{changeRequestName(entry.changeRequestNumber)}</Chip>
    )
  switch (entry.action) {
    case 'PUBLISH': {
      const warnings = details.warnings?.length ?? 0
      return (
        <>
          <div className="event__line">
            {verb}
            <span className="vstatus vstatus--published">{`v${String(details.versionNo)}`}</span>
            {details.forkedFromVersionId === undefined ? null : (
              <span className="muted">copied from the seeded version as it was published</span>
            )}
            <span className="event__id" title={entry.rulesetVersionId}>
              {shortId(entry.rulesetVersionId)}
            </span>
          </div>
          <span className="event__delta">{publishedText(details.rules ?? 0, warnings)}</span>
        </>
      )
    }
    case 'CHANGE_PROPOSED':
      return (
        <>
          <div className="event__line">
            {verb}
            {request}
            <span className="muted">on</span>
            <span className="mono">{`v${String(details.versionNo)}`}</span>
          </div>
          <Quote text={details.requestText} />
          <span className="event__delta">{proposedText(details)}</span>
        </>
      )
    case 'CHANGE_APPROVED': {
      const changes = details.diff ? conditionChanges(details.diff) : []
      const versionNo = details.versionNo ?? 0
      return (
        <>
          <div className="event__line">
            {verb}
            {request}
            <span className="muted">published</span>
            <span className="vstatus vstatus--published">{`v${String(versionNo)}`}</span>
            <span className="muted">by</span>
            <span>{PERSON}</span>
            <Seal kicker="Approved" line={timeOf(entry.at)} inline />
          </div>
          {changes.length > 0 ? (
            <span className="event__delta">
              {changes.map((change, index) => (
                <span key={change.key}>
                  {index > 0 ? ' · ' : ''}
                  {`${change.rule} ${change.field} `}
                  <span className="arrow">{`${change.before} → ${change.after}`}</span>
                </span>
              ))}
            </span>
          ) : null}
          <Quote text={details.note} />
          <details>
            <summary>Show the request, the diff and the regression</summary>
            <div className="event__more">
              <Quote text={details.requestText} />
              {details.regression ? (
                <span className="event__delta">{flipsText(details.regression)}</span>
              ) : null}
              {details.diff ? (
                <DiffView
                  diff={details.diff}
                  language={language}
                  fields={fields}
                  beforeLabel={`v${String(versionNo - 1)}`}
                  afterLabel={`v${String(versionNo)}`}
                />
              ) : null}
              {details.regression ? (
                <RegressionReport regression={details.regression} baseVersionNo={versionNo - 1} />
              ) : null}
            </div>
          </details>
        </>
      )
    }
    case 'CHANGE_REJECTED':
      return (
        <>
          <div className="event__line">
            {verb}
            {request}
            <span className="muted">by</span>
            <span>{PERSON}</span>
            <Seal kicker="Rejected" line={timeOf(entry.at)} inline />
          </div>
          <Quote text={details.note} />
          {details.requestText ? (
            <details>
              <summary>Show the request</summary>
              <div className="event__more">
                <Quote text={details.requestText} />
              </div>
            </details>
          ) : null}
        </>
      )
    case 'GAP_ACKNOWLEDGED': {
      const resolution = details.acknowledgement?.resolution
      return (
        <>
          <div className="event__line">
            {verb}
            {details.id === undefined ? null : <Chip>{details.id}</Chip>}
            <span className="muted">{`${details.kind ? KIND_LABELS[details.kind] : 'Finding'}, on`}</span>
            <span className="vstatus vstatus--draft">{`Draft v${String(details.versionNo)}`}</span>
          </div>
          {resolution === undefined ? null : (
            <span className="event__resolution">{RESOLUTION_LABELS[resolution]}</span>
          )}
          <Quote text={details.acknowledgement?.note} />
        </>
      )
    }
    case 'RESET':
      return (
        <div className="event__line">
          {verb}
          <span className="muted">the seeded data was reset</span>
        </div>
      )
  }
}

/** A text a person wrote, quoted in its own direction: a Hebrew note right to left inside the English log. */
function Quote({ text }: { text: string | null | undefined }): ReactNode {
  if (text === null || text === undefined || text.trim() === '') {
    return null
  }
  const dir = directionOfText(text)
  const language: ContentLanguage = dir === 'rtl' ? 'he' : 'en'
  return (
    <div className="he-quote">
      <p dir={dir} lang={language}>
        {isolated(text, language)}
      </p>
    </div>
  )
}

/** The changed comparisons of an approval's diff, as the row's line of what changed writes them. */
function conditionChanges(diff: Diff) {
  return unifiedRows(diff)
    .filter((row) => row.kind === 'condition')
    .map((row) => ({
      key: `${row.key}:${row.field}`,
      rule: row.key,
      field: row.field,
      before: comparisonText(row.before),
      after: comparisonText(row.after),
    }))
}

/**
 * What a proposal recorded: "2 patches · considered R-170, R-410, R-020, R-200, R-320 · 2,140 tokens"; an entry
 * written before 2026-09-28 holds the count of patches alone.
 */
function proposedText({ patches = 0, candidates, tokens }: Details): string {
  const parts = [`${String(patches)} patch${patches === 1 ? '' : 'es'}`]
  if (candidates !== undefined && candidates.length > 0) {
    parts.push(`considered ${candidates.join(', ')}`)
  }
  if (tokens !== undefined) {
    parts.push(`${COUNT.format(tokens)} tokens`)
  }
  return parts.join(' · ')
}

/** What a publication recorded: "20 rules", and the warnings it left open. */
function publishedText(rules: number, warnings: number): string {
  const open =
    warnings === 0 ? '' : ` · ${String(warnings)} warning${warnings === 1 ? '' : 's'} left open`
  return `${String(rules)} rules${open}`
}

/** An approval's regression in one line: "12 flipped · 6 approved→declined · 6 manual review→declined". */
function flipsText(regression: Regression): string {
  const transitions = Object.entries(regression.transitions).map(([key, count]) => {
    const [from, to] = key.split(' → ') as [DecisionStatus, DecisionStatus]
    return `${String(count)} ${DECISION_LABELS[from].toLowerCase()}→${DECISION_LABELS[to].toLowerCase()}`
  })
  return [`${String(regression.flips.length)} flipped`, ...transitions].join(' · ')
}

/** An id as the log's margin writes it: its first four characters and its last two, the whole on hover. */
function shortId(id: string): string {
  return `${id.slice(0, 4)}…${id.slice(-2)}`
}
