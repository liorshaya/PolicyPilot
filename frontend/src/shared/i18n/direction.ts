/**
 * Direction and bidirectional text (NFR-5; Document 2, Frontend Architecture, key decision 5): the interface chrome
 * is English and left-to-right, and only a content block turns around, driven by the language of the policy or of
 * the message it holds.
 */

import { createElement, Fragment, type ReactElement, type ReactNode } from 'react'

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

/**
 * A number's digits grouped by thousands with a comma and the same whatever the machine's locale; never rounded, and
 * with as many decimals as it was written with when that is given ("1,493.10" keeps its zero).
 */
function grouped(value: number, fractionDigits?: number): string {
  const written = fractionDigits === undefined ? String(Math.abs(value)) : Math.abs(value).toFixed(fractionDigits)
  const [whole, fraction] = written.split('.')
  const digits = whole!.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return fraction === undefined ? digits : `${digits}.${fraction}`
}

/**
 * The text of a number token: its sign, its digits, and its unit where the language puts it; a percent is closed up
 * in both languages (the spec, section 03, Numbers).
 */
function spelled(
  value: number,
  unit: string | undefined,
  language: ContentLanguage,
  fractionDigits?: number,
): string {
  const sign = value < 0 ? MINUS_SIGN : ''
  const digits = grouped(value, fractionDigits)
  if (unit === undefined) {
    return `${sign}${digits}`
  }
  if (unit === '%') {
    return `${sign}${digits}%`
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
  fractionDigits?: number,
): ReactElement {
  return createElement('bdi', { dir: 'ltr' }, spelled(value, unit, language, fractionDigits))
}

/** A number as a text writes it: "8,000", "1,493.10", "84". */
const WRITTEN_NUMBER = String.raw`\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?`

/**
 * The machine tokens a Hebrew text may carry (the spec, section 03, the bidi law, clause 5), in the order they are tried
 * at each place: a date before the numbers it is made of, a range before its two ends, then a number with its sign
 * and unit, a rule id, a version and a field name. A hyphen is a minus only where no letter or digit precedes it, so
 * the hyphen of a Hebrew prefix ("מ-8,000") stays in the words.
 */
const MACHINE_TOKEN = new RegExp(
  [
    String.raw`(?<date>\d{4}-\d{2}-\d{2})`,
    String.raw`(?<from>${WRITTEN_NUMBER})–(?<to>${WRITTEN_NUMBER})(?:[ \u00a0]?(?<rangeUnit>₪|%))?`,
    String.raw`(?<number>(?:(?<![\p{L}\p{N}])[−-])?(?:${WRITTEN_NUMBER}))(?:[ \u00a0]?(?<unit>₪)|(?<percent>%))?`,
    String.raw`(?<mono>\bR-\d{2,4}\b|\bv\d+(?:\.\d+)*\b|\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\b)`,
  ].join('|'),
  'gu',
)

/** A written number's value and the decimals it was written with. */
function numberOf(written: string): { value: number; fractionDigits: number } {
  const plain = written.replace(/[,]/g, '').replace(/^[−-]/, '-')
  return { value: Number(plain), fractionDigits: plain.split('.')[1]?.length ?? 0 }
}

/** The text of a written number, as numberToken would write it: the minus U+2212, the decimals kept. */
function numberText(written: string): string {
  const { value, fractionDigits } = numberOf(written)
  return spelled(value, undefined, 'he', fractionDigits)
}

/**
 * A content block's text with every machine token inside Hebrew isolated, as the bidi law asks (the spec, section 03):
 * a number, an amount or a percent through numberToken, a range and a date as one left-to-right isolate, and a rule
 * id, a version or a field name in the mono. English content reads left to right already and is returned as it is.
 */
export function isolated(text: string, language: ContentLanguage): ReactNode[] {
  if (language !== 'he') {
    return [text]
  }
  const parts: ReactNode[] = []
  let at = 0
  for (const match of text.matchAll(MACHINE_TOKEN)) {
    const groups = match.groups!
    if (match.index > at) {
      parts.push(text.slice(at, match.index))
    }
    const key = match.index
    if (groups.date !== undefined) {
      parts.push(createElement('bdi', { key, dir: 'ltr' }, groups.date))
    } else if (groups.from !== undefined) {
      const unit = groups.rangeUnit
      const range = `${numberText(groups.from)}–${numberText(groups.to!)}`
      parts.push(
        createElement(
          'bdi',
          { key, dir: 'ltr' },
          unit === undefined ? range : unit === '%' ? `${range}%` : `${range}${NO_BREAK_SPACE}${unit}`,
        ),
      )
    } else if (groups.number !== undefined) {
      const { value, fractionDigits } = numberOf(groups.number)
      const unit = groups.unit ?? groups.percent
      parts.push(createElement(Fragment, { key }, numberToken(value, unit, 'he', fractionDigits)))
    } else {
      parts.push(createElement('bdi', { key, dir: 'ltr', className: 'mono' }, groups.mono))
    }
    at = match.index + match[0].length
  }
  if (at < text.length) {
    parts.push(text.slice(at))
  }
  return parts
}
