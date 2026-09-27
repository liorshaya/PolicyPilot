import type { FindingKind } from '../../api/types'
import { KIND_LABELS } from './findingKinds'
import './Severity.css'

/**
 * The mark of each kind, by what the publish gate does with it: a square must be acknowledged before publishing, a bar is
 * an instruction planted in the text, a triangle may stay open.
 */
const MARKS: Record<FindingKind, 'error' | 'injection' | 'warning'> = {
  conflict: 'error',
  unsupported: 'error',
  gap: 'error',
  injection: 'injection',
  ambiguity: 'warning',
  duplicate: 'warning',
}

interface SeverityProps {
  kind: FindingKind
  /** The finding's own code, written before its kind: "F-1 Conflict". */
  code?: string
}

/** A finding's mark with its word beside it, never the mark alone (the spec, section 06). */
export function Severity({ kind, code }: SeverityProps) {
  return (
    <span className={`sev sev--${MARKS[kind]}`}>
      {code === undefined ? KIND_LABELS[kind] : `${code} ${KIND_LABELS[kind]}`}
    </span>
  )
}
