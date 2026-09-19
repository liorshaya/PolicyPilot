import './StatusTag.css'
import {
  DECISION_LABELS,
  VERSION_LABELS,
  type DecisionStatus,
  type VersionStatus,
} from './decisionLabels'

interface DecisionTagProps {
  status: DecisionStatus
  /** A quieter form for a table cell, where the row already carries the emphasis. */
  quiet?: boolean
}

/**
 * A decision as a tag: approved, declined, manual review or evaluation error. These colours are a family of their
 * own, never the brand blue, so a business outcome can never be mistaken for chrome.
 */
export function DecisionTag({ status, quiet = false }: DecisionTagProps) {
  return (
    <span className={`tag tag--${status}${quiet ? ' tag--quiet' : ''}`}>
      <span className="tag__dot" aria-hidden="true" />
      {DECISION_LABELS[status]}
    </span>
  )
}

/** A version's status: stated plainly, in the chrome's own greys and blue. */
export function VersionTag({ status }: { status: VersionStatus }) {
  return (
    <span className={`version-tag version-tag--${status.toLowerCase()}`}>
      {VERSION_LABELS[status]}
    </span>
  )
}
