import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/** Vitest runs from the project root, where its configuration lives. */
const tokensCss = readFileSync(resolve(process.cwd(), 'src/styles/tokens.css'), 'utf8')
/** The Register's own CSS, whose layer 1 tokens.css carries verbatim (Document 9, phase 0, build item 2). */
const specCss = readFileSync(resolve(process.cwd(), '../docs/design/register.css'), 'utf8')

type Theme = 'light' | 'dark'
const THEMES: Theme[] = ['light', 'dark']

/**
 * The custom properties a stylesheet declares for each theme: `:root` is the light theme (the aliases included), and
 * `:root[data-theme='dark']` is the dark one, which overrides the light value wherever it declares a name.
 */
function declarations(css: string): Record<Theme, Map<string, string>> {
  const found = { light: new Map<string, string>(), dark: new Map<string, string>() }
  const text = css.replace(/\/\*[\s\S]*?\*\//g, '')
  for (const [, selector, body] of text.matchAll(
    /(:root(?:\[data-theme='dark'\])?)\s*\{([^{}]*)\}/g,
  )) {
    const theme: Theme = selector === ':root' ? 'light' : 'dark'
    for (const [, name, value] of body!.matchAll(/--([a-z0-9-]+):\s*([^;]+);/g)) {
      found[theme].set(name!, value!.trim())
    }
  }
  return found
}

const tokens = declarations(tokensCss)
const spec = declarations(specCss.split('/* ---------- Layer 2')[0]!)

/** The value a theme gives a token, following var() as the browser does on the one element both themes set. */
function token(name: string, theme: Theme): string {
  const value = tokens[theme].get(name) ?? tokens.light.get(name)
  if (value === undefined) {
    throw new Error(`tokens.css declares no --${name}`)
  }
  const indirect = /^var\(--([a-z0-9-]+)\)$/.exec(value)?.[1]
  return indirect === undefined ? value : token(indirect, theme)
}

function luminance(hex: string): number {
  if (!/^#[0-9a-f]{6}$/i.test(hex)) {
    throw new Error(`${hex} is not a colour this test can measure`)
  }
  const channels = [1, 3, 5].map((at) => parseInt(hex.slice(at, at + 2), 16) / 255)
  const linear = channels.map((value) =>
    value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4),
  )
  return 0.2126 * linear[0]! + 0.7152 * linear[1]! + 0.0722 * linear[2]!
}

/** WCAG 2.1's contrast ratio. */
function ratio(foreground: string, background: string): number {
  const [light, dark] = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return (light! + 0.05) / (dark! + 0.05)
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
  })
})
