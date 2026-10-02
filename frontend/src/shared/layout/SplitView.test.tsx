import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState, type ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { media, rule, stylesheet } from '../../test/css'
import { windowAt } from '../../test/viewport'
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
      <SplitView
        main={<p>The thread</p>}
        side={<p>The paragraph</p>}
        sideOpen
        sideLabel="Paragraph 2"
      />,
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

  // the spec, section 08 (v3.6): the sheet's scroll box, the filled section and the margin are containing blocks, so
  // nothing positioned inside them stretches the page
  it('makes each scroller a containing block, so the page itself never scrolls on a desktop', () => {
    const css = stylesheet('shared/layout/SplitView.css')

    for (const scroller of ['.sheet__scroll', '.sheet--fill > section', '.margin']) {
      expect(rule(css, scroller), scroller).toMatchObject({
        overflow: 'auto',
        position: 'relative',
      })
    }
  })
})

/**
 * The margin below 1200px (the spec, section 08, Sheet and margin, v3.9): a drawer over the sheet, closed until the
 * reader asks for it, shut by its Close or Esc, which give the focus back to what opened it; on a phone the next section
 * of the page, brought into view when its row changes, the reader taken back when it closes.
 */
describe('SplitView below 1200px', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  /** A screen whose button opens the margin, as Documents opens the Policies drawer. */
  function Opened({ children }: { children?: ReactNode }) {
    const [open, setOpen] = useState(false)
    return (
      <>
        <button type="button" onClick={() => setOpen(true)}>
          Documents
        </button>
        <SplitView
          main={<p>The policy</p>}
          side={
            <>
              <p>The documents</p>
              {children}
            </>
          }
          sideOpen={open}
          onCloseSide={() => setOpen(false)}
          closeButton
        />
      </>
    )
  }

  it('is a drawer with its Close at its head, shut by Close or Esc, the focus back on what opened it', async () => {
    windowAt(1024)
    const user = userEvent.setup()
    render(<Opened />)
    const opener = screen.getByRole('button', { name: 'Documents' })

    await user.click(opener)
    expect(screen.getByRole('complementary')).toHaveTextContent('The documents')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('complementary')).toBeNull()
    expect(opener).toHaveFocus()

    await user.click(opener)
    await user.click(
      within(screen.getByRole('complementary')).getByRole('button', { name: 'Close' }),
    )
    expect(screen.queryByRole('complementary')).toBeNull()
    expect(opener).toHaveFocus()
  })

  it('leaves Esc to a popover over the drawer and to a field the reader is typing in', async () => {
    windowAt(1024)
    const user = userEvent.setup()
    render(
      <Opened>
        <div role="dialog" aria-label="Help">
          <button type="button">In the popover</button>
        </div>
        <label>
          Title
          <input />
        </label>
      </Opened>,
    )
    await user.click(screen.getByRole('button', { name: 'Documents' }))

    await user.click(screen.getByRole('button', { name: 'In the popover' }))
    await user.keyboard('{Escape}')
    expect(screen.getByRole('complementary')).toBeInTheDocument()

    await user.type(screen.getByRole('textbox', { name: 'Title' }), 'מדיניות')
    await user.keyboard('{Escape}')
    expect(screen.getByRole('complementary')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('מדיניות')
  })

  it('is no drawer from 1200px: the margin stands beside the sheet with no Close, and Esc leaves it', async () => {
    windowAt(1376)
    const user = userEvent.setup()
    const onCloseSide = vi.fn()
    render(
      <SplitView
        main={<p>The policy</p>}
        side={<p>The documents</p>}
        sideOpen
        onCloseSide={onCloseSide}
        closeButton
      />,
    )

    expect(
      within(screen.getByRole('complementary')).queryByRole('button', { name: 'Close' }),
    ).toBeNull()
    await user.keyboard('{Escape}')
    expect(onCloseSide).not.toHaveBeenCalled()
  })

  it('on a phone brings the margin into view for each row, and the reader back to where they were when it closes', () => {
    windowAt(390)
    const shown = vi.spyOn(Element.prototype, 'scrollIntoView')
    const scrolled = vi.fn()
    vi.stubGlobal('scrollTo', scrolled)
    vi.stubGlobal('scrollY', 7200)
    const { rerender } = render(
      <SplitView main={<p>The cases</p>} side={<p>Case 17</p>} sideOpen sideKey="17" />,
    )

    const margin = screen.getByRole('complementary')
    expect(shown.mock.contexts).toStrictEqual([margin])

    rerender(<SplitView main={<p>The cases</p>} side={<p>Case 18</p>} sideOpen sideKey="18" />)
    expect(shown.mock.contexts).toStrictEqual([margin, margin])

    rerender(<SplitView main={<p>The cases</p>} side={null} sideOpen={false} sideKey={null} />)
    expect(scrolled).toHaveBeenCalledWith({ top: 7200 })
  })
})
