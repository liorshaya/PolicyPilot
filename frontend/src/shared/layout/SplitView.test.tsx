import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { media, rule, stylesheet } from '../../test/css'
import { SplitView } from './SplitView'

/**
 * The body of a working screen (the spec, section 08): a sheet, the only white area, and a paper margin beside it at
 * 340px, 440px when it holds a trace; the sheet and the margin scroll on their own, and below 1200px the margin becomes
 * a drawer over the sheet.
 */
const css = stylesheet('shared/layout/SplitView.css')

describe('SplitView', () => {
  it('lays the sheet and the margin side by side, the margin at --margin-w', () => {
    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen />,
    )
    const body = container.querySelector('.ws-body')!

    expect(body.querySelector(':scope > section.sheet > .sheet__scroll')).toHaveTextContent(
      'The table',
    )
    expect(body.querySelector(':scope > aside.margin')).toHaveTextContent('The rule')
    expect(rule(css, '.ws-body')['grid-template-columns']).toBe('minmax(0, 1fr) var(--margin-w)')
  })

  it('names the margin by what it holds, so a reader finds it among the landmarks', () => {
    const { getByRole } = render(
      <SplitView main={<p>The thread</p>} side={<p>The paragraph</p>} sideOpen sideLabel="Paragraph 2" />,
    )

    expect(getByRole('complementary', { name: 'Paragraph 2' })).toHaveTextContent('The paragraph')
  })

  it('widens the margin for a trace, and gives the sheet the whole width when the margin is closed', () => {
    const { container, rerender } = render(
      <SplitView main={<p>The cases</p>} side={<p>The trace</p>} sideOpen wide />,
    )

    expect(container.querySelector('.ws-body')).toHaveClass('ws-body--wide-margin')
    expect(rule(css, '.ws-body--wide-margin')['grid-template-columns']).toBe(
      'minmax(0, 1fr) var(--margin-w-wide)',
    )

    rerender(<SplitView main={<p>The cases</p>} side={<p>The trace</p>} sideOpen={false} />)
    expect(container.querySelector('.ws-body')).toHaveClass('ws-body--single')
    expect(container.querySelector('aside')).toBeNull()
  })

  it("makes the table the sheet's only scroller: the section fills the sheet, the table takes what is left", () => {
    // the spec's composed screens, section 10, and the board's note of phase 2: the Rules and Cases sheets in phase 3
    const { container } = render(
      <SplitView main={<section>The table</section>} side={<p>The rule</p>} sideOpen fill />,
    )

    const sheet = container.querySelector('section.sheet')!
    expect(sheet).toHaveClass('sheet--fill')
    expect(sheet.querySelector('.sheet__scroll')).toBeNull()
    expect(rule(css, '.sheet--fill > section')).toMatchObject({
      display: 'flex',
      'flex-direction': 'column',
      flex: '1',
      'min-height': '0',
    })
    expect(rule(css, '.sheet--fill > section > .table-scroll').flex).toBe('1')
    expect(rule(css, '.sheet--fill > section > .sheet__foot')['margin-top']).toBe('auto')
  })

  it('holds a sheet in the margin, for a trace or a review, as the spec draws the Cases screen', () => {
    const { container } = render(
      <SplitView
        main={<p>The cases</p>}
        side={<div className="sheet">The trace</div>}
        sideOpen
        sideSheet
      />,
    )

    expect(container.querySelector('aside')).toHaveClass('margin', 'margin--sheet')
    expect(rule(css, '.margin--sheet > .sheet')).toStrictEqual({ margin: '0', flex: '1' })
  })

  it('turns the margin into a drawer over the sheet below 1200px', () => {
    const narrow = media(css, '(max-width: 1199px)')

    expect(rule(narrow, '.ws-body')['grid-template-columns']).toBe('minmax(0, 1fr)')
    expect(rule(narrow, '.margin').position).toBe('absolute')
    expect(rule(narrow, '.margin').width).toBe('var(--margin-w)')
    expect(rule(narrow, '.ws-body--wide-margin > .margin').width).toBe('var(--margin-w-wide)')
    expect(rule(narrow, '.margin')['box-shadow']).toBe('var(--shadow-float)')
  })
})
