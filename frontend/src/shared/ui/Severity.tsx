import type { FindingKind } from '../../api/types'
import { KIND_LABELS, KIND_MARKS } from './findingKinds'
import './Severity.css'

interface SeverityProps {
  kind: FindingKind
  /** The finding's own code, written before its kind: "F-1 Conflict". */
  code?: string
  /**
   * The code alone for the eye, as under a paragraph of the Policies screen (the spec, section 10: "F-3"); the kind
   * stays in the title and for a screen reader.
   */
  brief?: boolean
}

/** A finding's mark with its word beside it, never the mark alone (the spec, section 06). */
export function Severity({ kind, code, brief = false }: SeverityProps) {
  if (brief && code !== undefined) {
    return (
      <span className={`sev sev--${KIND_MARKS[kind]}`} title={`${code} ${KIND_LABELS[kind]}`}>
        {code}
        <span className="sr-only"> {KIND_LABELS[kind]}</span>
      </span>
    )
  }
  return (
    <span className={`sev sev--${KIND_MARKS[kind]}`}>
      {code === undefined ? KIND_LABELS[kind] : `${code} ${KIND_LABELS[kind]}`}
    </span>
  )
}
