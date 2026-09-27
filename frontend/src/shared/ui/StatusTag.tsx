import './StatusTag.css'
import {
  DECISION_LABELS,
  VERSION_LABELS,
  type DecisionStatus,
  type VersionStatus,
} from './decisionLabels'
import { Icon, type IconName } from './Icon'

/** The spec's class for each outcome of the engine, and its glyph; an evaluation error stays in ink, with none. */
const DECISION_TAGS: Record<DecisionStatus, { tag: string; glyph?: IconName }> = {
  approve: { tag: 'approve', glyph: 'check' },
  reject: { tag: 'decline', glyph: 'cross' },
  refer: { tag: 'refer', glyph: 'person' },
  error: { tag: 'error' },
}

interface DecisionTagProps {
  status: DecisionStatus
  /** The dot form, for a dense cell where the row already carries the emphasis. */
  quiet?: boolean
}

/**
 * A decision, the only coloured word in the product (the spec, section 06): Approved is a check, Declined a cross,
 * Manual review a person, and Evaluation error stays in ink. The colour never stands alone: the word is always there.
 */
export function DecisionTag({ status, quiet = false }: DecisionTagProps) {
  const { tag, glyph } = DECISION_TAGS[status]
  return (
    <span className={`tag tag--${tag}${quiet ? ' tag--quiet tag--dot' : ''}`}>
      {!quiet && glyph ? <Icon name={glyph} /> : null}
      {DECISION_LABELS[status]}
    </span>
  )
}

interface VersionTagProps {
  status: VersionStatus
  /** The version's number, written after its state: "Draft v2". */
  versionNo?: number
  /** A seeded version is read-only; it says so on the well instead of its state. */
  seeded?: boolean
}

/**
 * A version's state (the spec, section 04), sentence case in mono: dashed while nothing decides with it, solid when the
 * engine may use it, struck when replaced, and on the well when it is seeded and read-only.
 */
export function VersionTag({ status, versionNo, seeded = false }: VersionTagProps) {
  if (seeded) {
    return <span className="vstatus vstatus--seeded">Seeded · read-only</span>
  }
  return (
    <span className={`vstatus vstatus--${status.toLowerCase()}`}>
      {versionNo === undefined ? VERSION_LABELS[status] : `${VERSION_LABELS[status]} v${versionNo}`}
    </span>
  )
}
