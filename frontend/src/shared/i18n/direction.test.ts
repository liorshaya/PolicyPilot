import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { contentAttributes, directionOf, directionOfText, isolate, numberToken } from './direction'

// @requirement NFR-5

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

/**
 * A number inside Hebrew content (the Register spec: section 03, the bidi law, and section 01, "numbers behave"). It is
 * isolated left to right so it keeps its order, with the true minus, and with the shekel where each language puts it:
 * "150,000 ₪ in Hebrew and ₪150,000 in English chrome". The two characters that pass for others are built from their
 * code points: U+00A0, the non-breaking space, and U+2212, the minus sign.
 */
describe('numberToken', () => {
  const noBreak = String.fromCodePoint(0x00a0)
  const minus = String.fromCodePoint(0x2212)

  it('writes an amount in Hebrew as the number, a non-breaking space and ₪, inside <bdi dir="ltr">', () => {
    const { container } = render(numberToken(150000, '₪', 'he'))
    const token = container.querySelector('bdi')

    expect(token).toHaveAttribute('dir', 'ltr')
    expect(token?.textContent).toBe(`150,000${noBreak}₪`)
  })

  it('writes a negative number with the minus sign U+2212, not a hyphen', () => {
    const { container } = render(numberToken(-12))

    expect(container.querySelector('bdi')?.textContent).toBe(`${minus}12`)
  })

  it('puts ₪ before the number in the English chrome', () => {
    const { container } = render(numberToken(150000, '₪', 'en'))

    expect(container.querySelector('bdi')?.textContent).toBe('₪150,000')
  })

  it('never emits a bare <bdi> around a number', () => {
    for (const token of [
      numberToken(8000, '₪'),
      numberToken(-12),
      numberToken(24),
      numberToken(0, '₪', 'en'),
    ]) {
      const { container, unmount } = render(token)

      expect(container.querySelectorAll('bdi')).toHaveLength(1)
      expect(container.querySelector('bdi')).toHaveAttribute('dir', 'ltr')
      unmount()
    }
  })
})
