import type { AuditEntry, Diff, FieldSchema } from '../../api/types'
import { directionOfText, type ContentLanguage } from '../../shared/i18n/direction'
import { DiffView } from '../change/DiffView'
import { RegressionReport } from '../change/RegressionReport'
import type { Regression } from '../change/types'
import { RESOLUTION_LABELS } from '../rules/findings'
import { formatInstant } from './time'

/** The actions of the audit log, in the words a reader looks for (Document 2, Data Model: `audit_entry.action`). */
const ACTION_LABELS: Record<AuditEntry['action'], string> = {
  PUBLISH: 'Published',
  CHANGE_PROPOSED: 'Change proposed',
  CHANGE_APPROVED: 'Change approved',
  CHANGE_REJECTED: 'Change rejected',
  GAP_ACKNOWLEDGED: 'Gap acknowledged',
  RESET: 'Reset',
}

interface AuditEntryViewProps {
  entry: AuditEntry
  /** The rule set's language, for the diff an approval stored. */
  language: ContentLanguage
  fields?: FieldSchema[]
}

/**
 * One entry of the audit log: what happened, when, who did it, and what the entry recorded (Brief, demo step 4: "who
 * changed what, when and why"). An approval's diff and regression report are rendered from the JSON the entry stored,
 * never recomputed from the documents (Document 3, Structural diff).
 */
export function AuditEntryView({ entry, language, fields }: AuditEntryViewProps) {
  return (
    <article className="audit__entry">
      <header className="audit__entry-head">
        <h3 className="audit__action">{ACTION_LABELS[entry.action]}</h3>
        <time className="audit__time tabular" dateTime={entry.at}>
          {formatInstant(entry.at)}
        </time>
      </header>
      <p className="audit__by">
        By <span className="mono">{entry.actor}</span>
      </p>
      <Details entry={entry} language={language} fields={fields} />
    </article>
  )
}

function Details({ entry, language, fields }: AuditEntryViewProps) {
  const details = (entry.details ?? {}) as Record<string, unknown>
  const text = (key: string) => (typeof details[key] === 'string' ? details[key] : null)
  const count = (key: string) => (typeof details[key] === 'number' ? details[key] : 0)
  switch (entry.action) {
    case 'CHANGE_APPROVED': {
      const versionNo = count('versionNo')
      return (
        <div className="audit__details">
          <Said label="Request" text={text('requestText')} />
          <Said label="Note" text={text('note')} />
          {isDiff(details.diff) ? (
            <DiffView
              diff={details.diff}
              language={language}
              fields={fields}
              beforeLabel={`Version ${versionNo - 1}`}
              afterLabel={`Version ${versionNo}`}
            />
          ) : null}
          {isRegression(details.regression) ? (
            <RegressionReport regression={details.regression} baseVersionNo={versionNo - 1} />
          ) : null}
        </div>
      )
    }
    case 'CHANGE_REJECTED':
      return (
        <div className="audit__details">
          <Said label="Request" text={text('requestText')} />
          <Said label="Note" text={text('note')} />
        </div>
      )
    case 'CHANGE_PROPOSED': {
      const patches = count('patches')
      return (
        <p className="audit__fact">
          {`${patches} patch${patches === 1 ? '' : 'es'} proposed on version ${count('versionNo')}`}
        </p>
      )
    }
    case 'PUBLISH': {
      const warnings = Array.isArray(details.warnings) ? details.warnings.length : 0
      return (
        <div className="audit__details">
          {text('forkedFromVersionId') !== null ? (
            <p className="audit__fact">Copied from the seeded version as it was published</p>
          ) : null}
          <p className="audit__fact">
            {`${count('rules')} rules published, ${warnings} warning${warnings === 1 ? '' : 's'} left open`}
          </p>
        </div>
      )
    }
    case 'GAP_ACKNOWLEDGED': {
      const acknowledgement = (details.acknowledgement ?? {}) as {
        resolution?: keyof typeof RESOLUTION_LABELS
        note?: string
      }
      return (
        <div className="audit__details">
          <Said label="Finding" text={text('message')} />
          {acknowledgement.resolution !== undefined ? (
            <p className="audit__fact">{`Resolved: ${RESOLUTION_LABELS[acknowledgement.resolution]}`}</p>
          ) : null}
          <Said label="Note" text={acknowledgement.note ?? null} />
        </div>
      )
    }
    case 'RESET':
      return <p className="audit__fact">The seeded data was reset.</p>
  }
}

/** A text a person wrote, laid out in its own direction: a Hebrew request or note inside the English log. */
function Said({ label, text }: { label: string; text: string | null }) {
  if (text === null || text.trim() === '') {
    return null
  }
  const dir = directionOfText(text)
  return (
    <p className="audit__said">
      <span className="audit__said-label">{label}</span>{' '}
      <span dir={dir} lang={dir === 'rtl' ? 'he' : undefined}>
        {text}
      </span>
    </p>
  )
}

function isDiff(value: unknown): value is Diff {
  const diff = value as Partial<Diff> | null
  return typeof diff?.rules === 'object' && typeof diff.fields === 'object'
}

function isRegression(value: unknown): value is Regression {
  const regression = value as Partial<Regression> | null
  return typeof regression?.decisions === 'number' && Array.isArray(regression.flips)
}
