import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/** Vitest runs from the project root, where its configuration lives. */
const css = readFileSync(resolve(process.cwd(), 'src/styles/tokens.css'), 'utf8')

/**
 * The contrast of every pair the interface actually puts on the screen (the brief: real contrast checks). The
 * values are read from the token file itself, so a colour cannot be changed without this test reading the new one,
 * and the ratios are WCAG 2.1's own formula. 4.5:1 is the threshold for text, 3:1 for a border or a bar.
 */

/** The value of a custom property, following one level of var() indirection as the file writes it. */
function token(name: string): string {
  const declared = new RegExp(`--${name}:\\s*([^;]+);`).exec(css)?.[1]?.trim()
  if (declared === undefined) {
    throw new Error(`tokens.css declares no --${name}`)
  }
  const indirect = /^var\(--([a-z0-9-]+)\)$/.exec(declared)?.[1]
  return indirect === undefined ? declared : token(indirect)
}

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((at) => parseInt(hex.slice(at, at + 2), 16) / 255)
  const linear = channels.map((value) =>
    value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4),
  )
  return 0.2126 * linear[0]! + 0.7152 * linear[1]! + 0.0722 * linear[2]!
}

function ratio(foreground: string, background: string): number {
  const [light, dark] = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return (light! + 0.05) / (dark! + 0.05)
}

/** Text that a person reads, on the surface it is drawn on. */
const TEXT: [string, string, string][] = [
  ['the main text on a working surface', 'color-text', 'color-surface'],
  ['the main text on the page', 'color-text', 'color-bg'],
  ['secondary text on a working surface', 'color-text-muted', 'color-surface'],
  ['secondary text on the page', 'color-text-muted', 'color-bg'],
  ['secondary text on a sunken surface', 'color-text-muted', 'color-surface-sunken'],
  ['secondary text on a selected row', 'color-text-muted', 'color-accent-wash'],
  ['a link or a primary action on a working surface', 'color-accent', 'color-surface'],
  ['a link on the wash it is selected in', 'color-accent', 'color-accent-wash'],
  ['a value a diff inserts, on the accent wash', 'color-text', 'color-accent-wash'],
  ['approved, on its own wash', 'color-approved-text', 'color-approved-bg'],
  ['approved, in a table cell', 'color-approved-text', 'color-surface'],
  ['declined, on its own wash', 'color-declined-text', 'color-declined-bg'],
  ['declined, in a table cell', 'color-declined-text', 'color-surface'],
  ['manual review, on its own wash', 'color-review-text', 'color-review-bg'],
  ['manual review, in a table cell', 'color-review-text', 'color-surface'],
  ['an evaluation error, on its own wash', 'color-error-text', 'color-error-bg'],
]

describe('the palette', () => {
  it.each(TEXT)('reads %s at 4.5:1 or better', (_what, foreground, background) => {
    expect(ratio(token(foreground), token(background))).toBeGreaterThanOrEqual(4.5)
  })

  // WCAG 2.1 asks 3:1 of a focus indicator and of anything non-textual that carries meaning
  it('draws the focus ring and the selection bar against their surfaces at 3:1 or better', () => {
    expect(ratio(token('color-focus'), token('color-surface'))).toBeGreaterThanOrEqual(3)
    expect(ratio(token('color-focus'), token('color-bg'))).toBeGreaterThanOrEqual(3)
    expect(ratio(token('color-selected-bar'), token('color-accent-wash'))).toBeGreaterThanOrEqual(3)
    expect(ratio(token('color-selected-bar'), token('color-surface'))).toBeGreaterThanOrEqual(3)
  })

  it('keeps the sidebar readable on the ink it is drawn on', () => {
    expect(ratio(token('color-text-inverse'), token('pp-midnight'))).toBeGreaterThanOrEqual(4.5)
  })

  it('writes white on the primary action, and on the ink behind it', () => {
    expect(ratio(token('color-text-inverse'), token('color-accent'))).toBeGreaterThanOrEqual(4.5)
    expect(ratio(token('color-text-inverse'), token('pp-blue-strong'))).toBeGreaterThanOrEqual(4.5)
  })

  it('keeps the six brand colours of the palette exactly as they were given', () => {
    expect({
      ink: token('pp-midnight'),
      blue: token('pp-blue'),
      cloud: token('pp-cloud'),
      steel: token('pp-steel'),
      surface: token('pp-surface'),
      border: token('pp-border'),
    }).toEqual({
      ink: '#142c43',
      blue: '#285a80',
      cloud: '#f7f9fb',
      steel: '#66788a',
      surface: '#ffffff',
      border: '#e5edf3',
    })
  })
})
