import type { ErrorDetail, FieldSchema } from '../../api/types'
import { domainText } from '../rules/fieldSchema'

/**
 * What the officer typed into Decide a case, as the case the API decides (Document 2, decide: one `case`), and the
 * API's refusal of it read back onto the fields (Document 3, case validation). The form checks nothing itself: the API
 * validates the case against the version's fields and the engine decides it, so a value that is not a number goes as
 * it was typed and comes back named under its input.
 */

/** U+2212, built from its code point because it passes for a hyphen in the source. */
const MINUS_SIGN = String.fromCodePoint(0x2212)

/**
 * A plain decimal number as an officer writes it: digits with an optional sign and fraction, a comma only between
 * groups of three ("72,000"), and a true minus (U+2212). "1,5" (a decimal comma), "0x10" and "1e3" are not written
 * that way and would otherwise be read as 15, 16 and 1000.
 */
const DECIMAL = /^[+-]?(?:\d{1,3}(?:,\d{3})+|\d*)(?:\.\d*)?$/

/** A number as an officer writes it, or the text itself when it is not one, which the API then names as such. */
function numberOf(text: string): number | string {
  const signed = text.replace(MINUS_SIGN, '-')
  if (!DECIMAL.test(signed) || !/\d/.test(signed)) {
    return text
  }
  const value = Number(signed.replaceAll(',', ''))
  return Number.isFinite(value) ? value : text
}

/**
 * The case: each field the officer filled, typed by the schema; an empty field is left out, and so is an optional box
 * left unticked, which the schema reads as its default (the spec, section 09: "optional, false when absent").
 */
export function caseOf(
  fields: FieldSchema[],
  values: Record<string, string | boolean>,
): Record<string, unknown> {
  const input: Record<string, unknown> = {}
  for (const field of fields) {
    if (field.derived === true) {
      continue
    }
    const value = values[field.name]
    if (field.type === 'boolean') {
      if (value === true || field.required === true) {
        input[field.name] = value === true
      }
      continue
    }
    const text = typeof value === 'string' ? value.trim() : ''
    if (text === '') {
      continue
    }
    input[field.name] = field.type === 'number' || field.type === 'integer' ? numberOf(text) : text
  }
  return input
}

/** The field a refusal's detail points at: "/case/term_months" names term_months (Document 2, CASE_INVALID). */
export function fieldOfPointer(detail: ErrorDetail): string | null {
  const match = /^\/case\/([^/]+)$/.exec(detail.path)
  return match ? match[1]! : null
}

/** What a problem of case validation says under the field it names (Document 3, step 1's four codes). */
export function problemText(code: string, field: FieldSchema): string {
  switch (code) {
    case 'CASE_REQUIRED_MISSING':
      return 'This field is required.'
    case 'CASE_TYPE_MISMATCH':
      return field.type === 'enum'
        ? 'Not one of its values.'
        : field.type === 'integer'
          ? 'Not an integer.'
          : `Not a ${field.type}.`
    case 'CASE_OUT_OF_RANGE': {
      const domain = domainText(field)
      return domain === null ? 'Outside its domain.' : `Outside its domain: ${domain}.`
    }
    case 'CASE_DERIVED_SUPPLIED':
      return 'The engine derives this field; a case does not supply it.'
    default:
      return 'Refused.'
  }
}
