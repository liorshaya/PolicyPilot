import { describe, expect, it } from 'vitest'
import { specRules, stylesheet, unported } from '../../test/css'

/**
 * The figures and the bar list of layer 5 of the spec's register.css (the Register spec, section 09, "Figures"),
 * carried into Figures.css rule by rule, which the case list's outcome row and a change's regression report both draw:
 * every selector the spec's "Figures" block writes for them, with the same declarations.
 */

describe('Figures.css', () => {
  it("carries every rule of the spec's figures and bar list, with the spec's declarations", () => {
    const figures = specRules('/* Figures */', '/* Policy document and its list */').filter(
      ([selector]) => !selector.startsWith('.outcome'),
    )

    expect(figures).toHaveLength(17)
    expect(unported(stylesheet('shared/ui/Figures.css'), figures)).toEqual([])
  })
})
