import type { FindingKind } from '../../api/types'

/** What each kind of finding is called (Document 4, the kind table; the spec, section 06). */
export const KIND_LABELS: Record<FindingKind, string> = {
  ambiguity: 'Ambiguity',
  conflict: 'Conflict',
  unsupported: 'Unsupported',
  gap: 'Gap',
  duplicate: 'Duplicate',
  injection: 'Instruction in the text',
}
