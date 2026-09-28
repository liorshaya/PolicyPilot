import { describe, expect, it } from 'vitest'
import { specRules, stylesheet, unported } from '../../test/css'

/**
 * The tables of layer 4 of the spec's register.css (the Register spec, section 07), carried into Table.css rule by rule:
 * every selector the spec's "Tables" block writes, with the same declarations, so the product draws its tables as the
 * spec does and a later edit that drifts from it fails here.
 */

describe('Table.css', () => {
  it("carries every rule of the spec's tables, with the spec's declarations", () => {
    const tables = specRules('/* Tables */', '/* Overlays */')

    expect(tables.length).toBeGreaterThan(70)
    expect(unported(stylesheet('shared/ui/Table.css'), tables)).toEqual([])
  })
})
