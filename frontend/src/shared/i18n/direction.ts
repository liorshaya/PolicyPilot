/**
 * Direction and bidirectional text (NFR-5; Document 2, Frontend Architecture, key decision 5): the interface chrome
 * is English and left-to-right, and only a content block turns around, driven by the language of the policy or of
 * the message it holds.
 */

import { createElement, type ReactElement } from 'react'

export type ContentLanguage = 'he' | 'en'
export type Direction = 'rtl' | 'ltr'

/** Hebrew letters, the script a policy's text uses. */
const HEBREW = /\p{Script=Hebrew}/u

/**
 * The isolates of the Unicode Bidirectional Algorithm, built from their code points: the characters themselves are
 * invisible, and a source file never carries one (Document 5, Supply Chain and Build Security: source integrity).
 */
const FIRST_STRONG_ISOLATE = String.fromCodePoint(0x2068)
const POP_DIRECTIONAL_ISOLATE = String.fromCodePoint(0x2069)

/** The direction a block of the given language is written in. */
export function directionOf(language: ContentLanguage): Direction {
  return language === 'he' ? 'rtl' : 'ltr'
}

/** The direction a piece of text is written in, when nothing declares its language. */
export function directionOfText(text: string): Direction {
  return HEBREW.test(text) ? 'rtl' : 'ltr'
}

/**
 * Wraps a left-to-right token (a rule id, a version, a number with a unit) so it keeps its own order inside a
 * right-to-left sentence. U+2068 opens a first-strong isolate and U+2069 closes it, which is what keeps
 * {@code R-330} from being read backwards next to Hebrew.
 */
export function isolate(token: string): string {
  return `${FIRST_STRONG_ISOLATE}${token}${POP_DIRECTIONAL_ISOLATE}`
}

/** The attributes a content block carries so the browser lays it out in its own direction. */
export function contentAttributes(language: ContentLanguage): {
  dir: Direction
  lang: ContentLanguage
} {
  return { dir: directionOf(language), lang: language }
}

/** U+00A0 and U+2212, built from their code points because each passes for another character in the source. */
const NO_BREAK_SPACE = String.fromCodePoint(0x00a0)
const MINUS_SIGN = String.fromCodePoint(0x2212)

/** A number's digits grouped by thousands with a comma, never rounded, and the same whatever the machine's locale. */
function grouped(value: number): string {
  const [whole, fraction] = String(Math.abs(value)).split('.')
  const digits = whole!.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return fraction === undefined ? digits : `${digits}.${fraction}`
}

/** The text of a number token: its sign, its digits, and its unit where the language puts it. */
function spelled(value: number, unit: string | undefined, language: ContentLanguage): string {
  const sign = value < 0 ? MINUS_SIGN : ''
  const digits = grouped(value)
  if (unit === undefined) {
    return `${sign}${digits}`
  }
  return language === 'he' ? `${sign}${digits}${NO_BREAK_SPACE}${unit}` : `${sign}${unit}${digits}`
}

/**
 * A number for Hebrew content, and the one way a number reaches it (the Register spec, section 03, the bidi law):
 * isolated left to right in a <bdi dir="ltr">, never a bare <bdi>, with the minus sign U+2212, and the unit after a
 * non-breaking space in Hebrew or before the number in the English chrome ("150,000 ₪" and "₪150,000", section 01).
 */
export function numberToken(
  value: number,
  unit?: string,
  language: ContentLanguage = 'he',
): ReactElement {
  return createElement('bdi', { dir: 'ltr' }, spelled(value, unit, language))
}
