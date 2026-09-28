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

/** A finding's mark: the square must be acknowledged, the bar is an instruction planted in the text, the triangle warns. */
export type FindingMark = 'error' | 'injection' | 'warning'

/**
 * The mark of each kind, by what the publish gate does with it (the spec, section 06): a square must be acknowledged
 * before publishing, a bar is an instruction planted in the text, a triangle may stay open.
 */
export const KIND_MARKS: Record<FindingKind, FindingMark> = {
  conflict: 'error',
  unsupported: 'error',
  gap: 'error',
  injection: 'injection',
  ambiguity: 'warning',
  duplicate: 'warning',
}
