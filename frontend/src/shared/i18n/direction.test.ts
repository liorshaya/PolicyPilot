import { render } from '@testing-library/react'
import { createElement } from 'react'
import { describe, expect, it } from 'vitest'
import {
  contentAttributes,
  directionOf,
  directionOfText,
  isolate,
  isolated,
  numberToken,
} from './direction'

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

/**
 * The machine tokens of a Hebrew text (the Register spec, section 03, the bidi law, clause 5: "every machine token inside
 * Hebrew is isolated and never wraps: rule ids, field names, amounts with sign and symbol, dates, versions, ranges"),
 * each its own isolate: a number through numberToken, left to right; a rule id, a version or a field name in the mono,
 * left to right. The texts are the spec's hard cases and the demo policy's own words.
 */
describe('isolated', () => {
  const noBreak = String.fromCodePoint(0x00a0)
  const minus = String.fromCodePoint(0x2212)

  function tokensOf(text: string, language: 'he' | 'en' = 'he') {
    const { container } = render(createElement('p', null, ...isolated(text, language)))
    return {
      text: container.textContent,
      isolates: [...container.querySelectorAll('bdi')].map((bdi) => ({
        text: bdi.textContent,
        dir: bdi.getAttribute('dir'),
        mono: bdi.classList.contains('mono'),
      })),
    }
  }

  it('makes an amount and its shekel one isolate, and leaves the hyphen of a Hebrew prefix alone', () => {
    const { text, isolates } = tokensOf('דחייה: הכנסה חודשית נטו נמוכה מ-8,000 ₪')

    expect(isolates).toEqual([{ text: `8,000${noBreak}₪`, dir: 'ltr', mono: false }])
    expect(text).toBe(`דחייה: הכנסה חודשית נטו נמוכה מ-8,000${noBreak}₪`)
  })

  it('writes a negative number with U+2212 whether it was typed with a hyphen or a minus', () => {
    expect(tokensOf('ההפרש: -12').isolates).toEqual([
      { text: `${minus}12`, dir: 'ltr', mono: false },
    ])
    expect(tokensOf(`ההפרש: ${minus}1,000 ₪`).isolates).toEqual([
      { text: `${minus}1,000${noBreak}₪`, dir: 'ltr', mono: false },
    ])
  })

  it('keeps the precision a number was written with, and closes up a percent', () => {
    expect(tokensOf('ההחזר החודשי 1,493.10 ₪, יחס של 9%').isolates).toEqual([
      { text: `1,493.10${noBreak}₪`, dir: 'ltr', mono: false },
      { text: '9%', dir: 'ltr', mono: false },
    ])
  })

  it('makes a range with its unit one isolate', () => {
    expect(tokensOf('רצועת הסימון הוזזה ל-9,000–10,000 ₪').isolates).toEqual([
      { text: `9,000–10,000${noBreak}₪`, dir: 'ltr', mono: false },
    ])
  })

  it('makes a date one isolate rather than three numbers', () => {
    expect(tokensOf('הגרסה פורסמה ב-2026-09-22').isolates).toEqual([
      { text: '2026-09-22', dir: 'ltr', mono: false },
    ])
  })

  it('sets a rule id, a version and a field name in the mono, each its own isolate', () => {
    expect(tokensOf('כלל R-330 בגרסה v1.0.0 בודק את has_guarantor').isolates).toEqual([
      { text: 'R-330', dir: 'ltr', mono: true },
      { text: 'v1.0.0', dir: 'ltr', mono: true },
      { text: 'has_guarantor', dir: 'ltr', mono: true },
    ])
  })

  it('isolates the numbers of an answer and leaves its words as they are', () => {
    const { text, isolates } = tokensOf('תקופת ההחזר המקסימלית היא 84 חודשים, לפי בקשה 17.')

    expect(isolates.map((token) => token.text)).toEqual(['84', '17'])
    expect(text).toBe('תקופת ההחזר המקסימלית היא 84 חודשים, לפי בקשה 17.')
  })

  it('leaves English content as it is: it reads left to right already', () => {
    expect(tokensOf('The minimum age is 21, R-330 refers it.', 'en')).toEqual({
      text: 'The minimum age is 21, R-330 refers it.',
      isolates: [],
    })
  })

  it('never emits a bare <bdi>', () => {
    const { isolates } = tokensOf(`R-170 דוחה כאשר ההכנסה נמוכה מ-8,000 ₪; ${minus}12; 2026-09-22; v1; 9%`)

    expect(isolates.length).toBeGreaterThan(0)
    expect(isolates.every((token) => token.dir === 'ltr')).toBe(true)
  })
})
