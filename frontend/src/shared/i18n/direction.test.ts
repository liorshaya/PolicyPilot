import { describe, expect, it } from 'vitest'
import { contentAttributes, directionOf, directionOfText, isolate } from './direction'

/**
 * Hebrew support (NFR-5; Document 6, Frontend Test Design: "RTL assertions check dir on Hebrew content blocks").
 * The Hebrew sentences here are the demo policy's own words.
 */
describe('direction', () => {
  it('turns a Hebrew block around and leaves an English one alone', () => {
    expect(directionOf('he')).toBe('rtl')
    expect(directionOf('en')).toBe('ltr')
  })

  it('reads the direction from the text when no language is declared', () => {
    expect(directionOfText('הלוואה אישית תינתן ליחיד שגילו 21 עד 70')).toBe('rtl')
    expect(directionOfText('Applicants must be at least 21 years old')).toBe('ltr')
  })

  it('isolates a rule id so it keeps its order inside a Hebrew sentence', () => {
    // U+2068 FIRST STRONG ISOLATE opens it and U+2069 POP DIRECTIONAL ISOLATE closes it (Unicode Bidirectional
    // Algorithm); built from their code points, because the characters themselves are invisible in the source
    const opens = String.fromCodePoint(0x2068)
    const closes = String.fromCodePoint(0x2069)

    expect(isolate('R-330')).toBe(`${opens}R-330${closes}`)
  })

  it('gives a content block its direction and its language', () => {
    expect(contentAttributes('he')).toEqual({ dir: 'rtl', lang: 'he' })
    expect(contentAttributes('en')).toEqual({ dir: 'ltr', lang: 'en' })
  })
})
