/**
 * Direction and bidirectional text (NFR-5; Document 2, Frontend Architecture, key decision 5): the interface chrome
 * is English and left-to-right, and only a content block turns around, driven by the language of the policy or of
 * the message it holds.
 */

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
