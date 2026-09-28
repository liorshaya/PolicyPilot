import type { FindingKind } from '../../api/types'
import { KIND_LABELS, KIND_MARKS } from './findingKinds'
import './Severity.css'

interface SeverityProps {
  kind: FindingKind
  /** The finding's own code, written before its kind: "F-1 Conflict". */
  code?: string
}

/** A finding's mark with its word beside it, never the mark alone (the spec, section 06). */
export function Severity({ kind, code }: SeverityProps) {
  return (
    <span className={`sev sev--${KIND_MARKS[kind]}`}>
      {code === undefined ? KIND_LABELS[kind] : `${code} ${KIND_LABELS[kind]}`}
    </span>
  )
}
