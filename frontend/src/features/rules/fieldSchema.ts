import type { FieldSchema, RuleSetDocument } from '../../api/types'
import { literalText, unitLabel } from './cellGrammar'

/**
 * A field of the case schema in the words both margin sections use for it (the spec, section 09, "Decide a case, and
 * the field schema"): Decide a case writes them after the field's name under its description, Fields writes them beside
 * the name. The DSL's `fields` block is the only source (Document 3, Field Schema).
 */

/**
 * The values a field may take (the spec's specimens: "0 to 120", "at least 1"). A lone lower bound of 0 is left out, as
 * the specimens leave it out of every amount and count; the exclusive bounds of Document 3 read "more than" and "less
 * than".
 */
export function domainText(field: FieldSchema): string | null {
  if (field.minimum !== undefined && field.maximum !== undefined) {
    return `${literalText(field.minimum)} to ${literalText(field.maximum)}`
  }
  const parts: string[] = []
  if (field.exclusiveMinimum !== undefined) {
    parts.push(`more than ${literalText(field.exclusiveMinimum)}`)
  } else if (field.minimum !== undefined && field.minimum !== 0) {
    parts.push(`at least ${literalText(field.minimum)}`)
  }
  if (field.exclusiveMaximum !== undefined) {
    parts.push(`less than ${literalText(field.exclusiveMaximum)}`)
  } else if (field.maximum !== undefined) {
    parts.push(`at most ${literalText(field.maximum)}`)
  }
  return parts.length === 0 ? null : parts.join(', ')
}

/**
 * What follows a field's name: its type, its unit, whether the case must carry it, and its domain; "integer · years ·
 * required · 0 to 120", "boolean · optional, false when absent", or for a derived field the rule that derives it,
 * "number · ₪ · derived by R-010".
 */
export function fieldSummary(field: FieldSchema, derivingRule?: string): string {
  const presence = field.derived
    ? `derived by ${derivingRule ?? 'a rule'}`
    : field.required
      ? 'required'
      : field.default === undefined
        ? 'optional'
        : `optional, ${literalText(field.default)} when absent`
  return [
    field.type,
    field.unit === undefined ? null : unitLabel(field.unit),
    presence,
    field.derived ? null : domainText(field),
  ]
    .filter((part) => part !== null)
    .join(' · ')
}

/** The rule whose `set` action derives a field (Document 3: a derived field is computed by a rule), if any. */
export function derivedBy(document: RuleSetDocument, field: string): string | undefined {
  return document.rules.find((rule) =>
    rule.actions.some((action) => action.type === 'set' && action.field === field),
  )?.id
}
