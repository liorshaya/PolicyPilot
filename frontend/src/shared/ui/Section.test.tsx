import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { rule, specRules, stylesheet, unported } from '../../test/css'
import { Button } from './Button'
import { Section } from './Section'

/**
 * A section of a sheet (the spec, sections 08 and 12): a row titled in 13/600 with its controls at the side, the count
 * or the note in the title's own quiet voice, and no explanatory subline under it; no card of its own inside the sheet.
 */
const css = stylesheet('shared/ui/Section.css')

describe('Section', () => {
  it('titles its row in 13/600, the quiet part beside the title, the controls at the side', () => {
    const { container } = render(
      <Section title="Decision table" subtitle="20 rules" actions={<Button size="sm">JSON</Button>}>
        <p>The rules</p>
      </Section>,
    )
    const row = container.querySelector('.sec')!

    expect(row.querySelector('.sec__title')).toHaveTextContent('Decision table · 20 rules')
    expect(row.querySelector('.sec__title .quiet')).toHaveTextContent('· 20 rules')
    expect(
      within(row.querySelector<HTMLElement>('.sec__side')!).getByRole('button'),
    ).toHaveTextContent('JSON')
    expect(rule(css, '.sec__title')['font-size']).toBe('var(--text-sm)')
    expect(rule(css, '.sec__title')['font-weight']).toBe('600')
  })

  it('is a region named by its title, with no border or fill of its own', () => {
    render(
      <Section title="Decisions">
        <p>The list</p>
      </Section>,
    )

    expect(screen.getByRole('region', { name: 'Decisions' })).toHaveTextContent('The list')
    expect(rule(css, 'section')).toStrictEqual({})
  })

  it('pads its body unless it is flush, for a table that reaches the edges', () => {
    const { container, rerender } = render(
      <Section title="Decisions">
        <p>The list</p>
      </Section>,
    )
    expect(container.querySelector('.sheet__body')).toHaveTextContent('The list')

    rerender(
      <Section title="Decisions" flush>
        <p>The list</p>
      </Section>,
    )
    expect(container.querySelector('.sheet__body')).toBeNull()
    expect(rule(css, '.sheet__body').padding).toBe('16px')
  })

  // the spec, section 08 (v3.9): a section's title row wraps its controls under its title when the sheet has no room
  // for both; the Rules sheet beside a drawer at 800px ran its controls 25px past the sheet
  it("carries the spec's section row, which wraps its controls under its title", () => {
    const section = specRules('.sec {', '.sheet__body {')

    expect(section).toHaveLength(4)
    expect(unported(css, section)).toEqual([])
    expect(rule(css, '.sec')['flex-wrap']).toBe('wrap')
    // and the controls wrap among themselves, shrinking to the row (the Rules draft's three at 721px)
    expect(rule(css, '.sec__side')).toMatchObject({ 'flex-wrap': 'wrap', 'min-width': '0' })
  })
})
