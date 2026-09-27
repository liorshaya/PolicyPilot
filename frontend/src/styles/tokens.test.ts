import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { stylesheet, themeDeclarations, THEMES, token, type Theme } from '../test/css'

/** The Register's own CSS, whose layer 1 tokens.css carries verbatim (Document 9, phase 0, build item 2). */
const specCss = readFileSync(resolve(process.cwd(), '../docs/design/register.css'), 'utf8')

const tokens = themeDeclarations(stylesheet('styles/tokens.css'))
const spec = themeDeclarations(specCss.split('/* ---------- Layer 2')[0]!)

function channels(colour: string): number[] {
  if (/^#[0-9a-f]{6}$/i.test(colour)) {
    return [1, 3, 5].map((at) => parseInt(colour.slice(at, at + 2), 16))
  }
  throw new Error(`${colour} is not a colour this test can measure`)
}

function luminance(hex: string): number {
  const linear = channels(hex)
    .map((value) => value / 255)
    .map((value) => (value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4)))
  return 0.2126 * linear[0]! + 0.7152 * linear[1]! + 0.0722 * linear[2]!
}

/** WCAG 2.1's contrast ratio. */
function ratio(foreground: string, background: string): number {
  const [light, dark] = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return (light! + 0.05) / (dark! + 0.05)
}

/** A translucent rgba() laid over an opaque background, as the browser composites it. */
function over(wash: string, background: string): string {
  const [r, g, b, alpha] = /^rgba\(([^)]+)\)$/.exec(wash)![1]!.split(',').map(Number)
  const mixed = [r!, g!, b!].map((value, at) =>
    Math.round(value * alpha! + channels(background)[at]! * (1 - alpha!)),
  )
  return `#${mixed.map((value) => value.toString(16).padStart(2, '0')).join('')}`
}

/** The spec's thresholds (section 12): "4.5:1 text, 3:1 marks, in both themes, tested". */
const TEXT = 4.5
const MARK = 3

/**
 * Every pair of the spec's section 02 that the product draws, as Document 9 names them for phase 0, one row each:
 * [foreground, background, the least ratio]. Every row runs in the light theme and in the dark theme.
 */
const PAIRS: [string, string, number][] = [
  // the ink, on the surfaces it is read on
  ['ink', 'paper', TEXT],
  ['ink', 'sheet', TEXT],
  ['ink-2', 'paper', TEXT],
  ['ink-2', 'sheet', TEXT],
  ['ink-2', 'well', TEXT],
  ['ink-2', 'accent-wash', TEXT],
  ['ink-3', 'paper', TEXT],
  ['ink-3', 'sheet', TEXT],
  ['ink-3', 'well', TEXT],
  ['ink-3', 'accent-wash', TEXT],
  // the accent: selection and links
  ['accent', 'sheet', TEXT],
  ['accent', 'accent-wash', TEXT],
  ['accent-text', 'sheet', TEXT],
  ['accent-text', 'accent-wash', TEXT],
  // the decisions: each text on its own tint and on the sheet, each mark against the sheet
  ['approve-text', 'approve-bg', TEXT],
  ['approve-text', 'sheet', TEXT],
  ['decline-text', 'decline-bg', TEXT],
  ['decline-text', 'sheet', TEXT],
  ['refer-text', 'refer-bg', TEXT],
  ['refer-text', 'sheet', TEXT],
  ['approve-mark', 'sheet', MARK],
  ['decline-mark', 'sheet', MARK],
  ['refer-mark', 'sheet', MARK],
  // the error toast; the primary button, ink on paper (paper on ink in the dark theme); the quiet marks
  ['toast-error-text', 'toast-error-bg', TEXT],
  ['ink-on-ink', 'ink', TEXT],
  ['mark-quiet', 'paper', MARK],
]

describe('the Register tokens', () => {
  it("carry layer 1 of the spec's register.css verbatim, in both themes", () => {
    const carried = (theme: Theme) =>
      Object.fromEntries([...spec[theme].keys()].map((name) => [name, tokens[theme].get(name)]))

    expect(carried('light')).toEqual(Object.fromEntries(spec.light))
    expect(carried('dark')).toEqual(Object.fromEntries(spec.dark))
    expect([...tokens.dark.keys()]).toEqual([...spec.dark.keys()])
  })

  it('hold at least 40 pairs across the two themes', () => {
    expect(PAIRS.length * THEMES.length).toBeGreaterThanOrEqual(40)
  })

  describe.each(THEMES)('in the %s theme', (theme) => {
    it.each(PAIRS)('draw --%s on --%s at %s:1 or better', (foreground, background, least) => {
      expect(ratio(token(foreground, theme), token(background, theme))).toBeGreaterThanOrEqual(
        least,
      )
    })

    // Document 9's second rule: the toast's hover wash, a literal of layer 4, is a token first, with its pair
    it("draw a toast's text on its hover wash at 4.5:1 or better", () => {
      const wash = token('toast-hover', theme)

      expect(
        ratio(token('ink-on-ink', theme), over(wash, token('ink', theme))),
      ).toBeGreaterThanOrEqual(TEXT)
      expect(
        ratio(token('toast-error-text', theme), over(wash, token('toast-error-bg', theme))),
      ).toBeGreaterThanOrEqual(TEXT)
    })
  })
})
