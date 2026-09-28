import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { rule, stylesheet } from '../../test/css'

/**
 * The tables of layer 4 of the spec's register.css (the Register spec, section 07), carried into Table.css rule by rule:
 * every selector the spec's "Tables" block writes, with the same declarations, so the product draws its tables as the
 * spec does and a later edit that drifts from it fails here.
 */

const spec = readFileSync(resolve(process.cwd(), '../docs/design/register.css'), 'utf8')
const product = stylesheet('shared/ui/Table.css')

/** The spec's "Tables" block: from its comment to the overlays that follow it. */
const tables = spec.slice(spec.indexOf('/* Tables */'), spec.indexOf('/* Overlays */'))

/** Each rule of a block as [selector, declarations], comments removed, a selector list split into its selectors. */
function rules(css: string): [string, Record<string, string>][] {
  const text = css.replace(/\/\*[\s\S]*?\*\//g, '')
  return [...text.matchAll(/([^{}]+)\{([^{}]*)\}/g)].flatMap(([, selectors, body]) => {
    const declarations: Record<string, string> = {}
    for (const declaration of body!.split(';')) {
      const colon = declaration.indexOf(':')
      if (colon > 0) {
        declarations[declaration.slice(0, colon).trim()] = declaration
          .slice(colon + 1)
          .trim()
          .replace(/\s+/g, ' ')
      }
    }
    return selectors!
      .split(',')
      .map((selector): [string, Record<string, string>] => [
        selector.trim().replace(/\s+/g, ' '),
        declarations,
      ])
  })
}

describe('Table.css', () => {
  it("carries every rule of the spec's tables, with the spec's declarations", () => {
    const ported = rules(tables)

    expect(ported.length).toBeGreaterThan(70)
    for (const [selector, declarations] of ported) {
      // Prettier may break a long value over lines; the value is the same
      const drawn = Object.fromEntries(
        Object.entries(rule(product, selector)).map(([name, value]) => [
          name,
          value.replace(/\s+/g, ' '),
        ]),
      )
      expect(drawn, selector).toMatchObject(declarations)
    }
  })
})
