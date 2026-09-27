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

  it('turns the margin into a drawer over the sheet below 1200px', () => {
    const narrow = media(css, '(max-width: 1199px)')

    expect(rule(narrow, '.ws-body')['grid-template-columns']).toBe('minmax(0, 1fr)')
    expect(rule(narrow, '.margin').position).toBe('absolute')
    expect(rule(narrow, '.margin').width).toBe('var(--margin-w)')
    expect(rule(narrow, '.ws-body--wide-margin > .margin').width).toBe('var(--margin-w-wide)')
    expect(rule(narrow, '.margin')['box-shadow']).toBe('var(--shadow-float)')
  })
})
