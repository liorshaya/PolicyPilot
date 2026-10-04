import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState, type ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { media, rule, specRules, stylesheet, token, unported } from '../../test/css'
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

  // the spec, section 08: "In a table, ↑ ↓ move the selection, ↵ opens the margin, Esc closes it", at every width
  it('is no drawer from 1200px: the margin stands beside the sheet with no Close at its head, and Esc still shuts what it holds', async () => {
    windowAt(1376)
    const user = userEvent.setup()
    const onCloseSide = vi.fn()
    render(
      <SplitView
        main={<p>The cases</p>}
        side={<p>Case 17</p>}
        sideOpen
        onCloseSide={onCloseSide}
        closeButton
      />,
    )

    expect(
      within(screen.getByRole('complementary')).queryByRole('button', { name: 'Close' }),
    ).toBeNull()
    await user.keyboard('{Escape}')
    expect(onCloseSide).toHaveBeenCalledOnce()
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

/** A token's length in pixels, as tokens.css sets it. */
const px = (name: string) => Number.parseFloat(token(name, 'light'))

/**
 * The margin's handle (the spec, section 08, Sheet and margin, v3.11): from 1200px the edge between the sheet and the
 * margin is a handle. Drawn toward the sheet it widens the margin and the sheet narrows by as much, never under the
 * margin's own width and never past what leaves the sheet --sheet-min; the arrows, Home and End move it, a double
 * click puts the margin back, and the width is remembered per screen in this browser. The expected widths are the
 * tokens': the margin's 340px and 440px, the sheet's floor, the 16px of a press.
 */
describe("SplitView, the margin's handle", () => {
  const OWN = px('margin-w')
  const OWN_WIDE = px('margin-w-wide')
  const SHEET_MIN = px('sheet-min')
  const STEP = px('s-4')
  /** The body of a 1440px window: what the rail leaves. */
  const BODY = 1440 - px('rail-w')
  /** Where the handle stands at first: the body's end less the margin. */
  const EDGE = BODY - OWN

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    localStorage.clear()
    document.head.replaceChildren()
  })

  /**
   * What jsdom does not do. The product's tokens are put on the page, as the browser has them, and the body is laid out
   * at a width, which a ResizeObserver reports when it starts and again whenever the window is resized.
   */
  function laidOut(width: number): { resize: (next: number) => void } {
    const tokens = document.createElement('style')
    tokens.textContent = stylesheet('styles/tokens.css')
    document.head.append(tokens)
    let bodyWidth = width
    const watching: (() => void)[] = []
    vi.stubGlobal(
      'ResizeObserver',
      class {
        private readonly report: () => void
        constructor(callback: () => void) {
          this.report = callback
          watching.push(callback)
        }
        observe = () => this.report()
        disconnect = () => undefined
      },
    )
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: Element,
    ) {
      return { width: this.classList.contains('ws-body') ? bodyWidth : 0 } as DOMRect
    })
    return {
      resize(next) {
        bodyWidth = next
        act(() => watching.forEach((report) => report()))
      },
    }
  }

  const handle = () => screen.getByRole('separator', { name: 'Width of the margin' })
  const body = (container: HTMLElement) => container.querySelector<HTMLElement>('.ws-body')!
  /** The width the margin is set to, null while it stands at its own. */
  const set = (container: HTMLElement) =>
    body(container).classList.contains('ws-body--set')
      ? body(container).style.getPropertyValue('--margin-set')
      : null

  /** Takes the handle where it stands and draws it to an x of the window, letting go there. */
  function draw(from: number, to: number): void {
    fireEvent.pointerDown(handle(), { pointerId: 1, button: 0, clientX: from })
    fireEvent.pointerMove(handle(), { pointerId: 1, clientX: to })
    fireEvent.pointerUp(handle(), { pointerId: 1, clientX: to })
  }

  it('stands on the edge between the sheet and the margin from 1200px: a stop of the keyboard named Width of the margin', () => {
    windowAt(1440)
    laidOut(BODY)
    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen sideLabel="Rule" />,
    )

    const margin = screen.getByRole('complementary', { name: 'Rule' })
    expect(handle()).toHaveAttribute('aria-orientation', 'vertical')
    expect(handle()).toHaveAttribute('tabindex', '0')
    expect(handle()).toHaveAttribute('aria-controls', margin.id)
    expect(handle()).toHaveAttribute(
      'title',
      'Drag to widen the margin; double-click to put it back',
    )
    expect(handle().previousElementSibling).toBe(container.querySelector('section.sheet'))
    expect(handle().nextElementSibling).toBe(margin)
    // at rest the margin is at its own width, between that and what leaves the sheet its floor
    expect(handle()).toHaveAttribute('aria-valuenow', String(OWN))
    expect(handle()).toHaveAttribute('aria-valuemin', String(OWN))
    expect(handle()).toHaveAttribute('aria-valuemax', String(BODY - SHEET_MIN))
    expect(set(container)).toBeNull()
  })

  it("starts a trace's margin at its own 440px", () => {
    windowAt(1440)
    laidOut(BODY)
    render(<SplitView main={<p>The cases</p>} side={<p>The trace</p>} sideOpen wide />)

    expect(handle()).toHaveAttribute('aria-valuenow', String(OWN_WIDE))
    expect(handle()).toHaveAttribute('aria-valuemin', String(OWN_WIDE))
    expect(handle()).toHaveAttribute('aria-valuemax', String(BODY - SHEET_MIN))
  })

  it('widens the margin by as much as the handle is drawn toward the sheet, taking the pointer while it is held', () => {
    windowAt(1440)
    laidOut(BODY)
    const taken = vi.spyOn(Element.prototype, 'setPointerCapture')
    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen />,
    )

    // the press is the handle's alone: its default, which selects text and moves the focus, is prevented
    expect(fireEvent.pointerDown(handle(), { pointerId: 7, button: 0, clientX: EDGE })).toBe(false)
    expect(taken.mock.contexts).toStrictEqual([handle()])
    expect(taken).toHaveBeenCalledWith(7)
    expect(handle()).toHaveClass('ws-handle--held')

    fireEvent.pointerMove(handle(), { pointerId: 7, clientX: EDGE - 120 })
    expect(set(container)).toBe(`${String(OWN + 120)}px`)
    expect(handle()).toHaveAttribute('aria-valuenow', String(OWN + 120))

    fireEvent.pointerMove(handle(), { pointerId: 7, clientX: EDGE - 40 })
    expect(set(container)).toBe(`${String(OWN + 40)}px`)

    fireEvent.pointerUp(handle(), { pointerId: 7, clientX: EDGE - 40 })
    expect(handle()).not.toHaveClass('ws-handle--held')
    // let go, the handle follows the pointer no more
    fireEvent.pointerMove(handle(), { pointerId: 7, clientX: EDGE - 300 })
    expect(set(container)).toBe(`${String(OWN + 40)}px`)
  })

  it("stops at what leaves the sheet its floor and at the margin's own width, and takes up again where the pointer comes back", () => {
    windowAt(1440)
    laidOut(BODY)
    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen />,
    )
    const most = BODY - SHEET_MIN

    fireEvent.pointerDown(handle(), { pointerId: 1, button: 0, clientX: EDGE })
    // far past the floor of the sheet: the margin stops at its end
    fireEvent.pointerMove(handle(), { pointerId: 1, clientX: 0 })
    expect(set(container)).toBe(`${String(most)}px`)
    expect(handle()).toHaveAttribute('aria-valuenow', String(most))
    // still past it: nothing moves until the pointer is back where the handle stopped
    fireEvent.pointerMove(handle(), { pointerId: 1, clientX: BODY - most - 60 })
    expect(set(container)).toBe(`${String(most)}px`)
    fireEvent.pointerMove(handle(), { pointerId: 1, clientX: BODY - most + 24 })
    expect(set(container)).toBe(`${String(most - 24)}px`)
    // and the other way, past the margin's own width: the margin is at its own again, set to nothing
    fireEvent.pointerMove(handle(), { pointerId: 1, clientX: EDGE + 200 })
    expect(set(container)).toBeNull()
    expect(handle()).toHaveAttribute('aria-valuenow', String(OWN))
    fireEvent.pointerUp(handle(), { pointerId: 1, clientX: EDGE + 200 })
  })

  it('is taken by the primary button alone', () => {
    windowAt(1440)
    laidOut(BODY)
    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen />,
    )

    fireEvent.pointerDown(handle(), { pointerId: 1, button: 2, clientX: EDGE })
    fireEvent.pointerMove(handle(), { pointerId: 1, clientX: EDGE - 120 })

    expect(handle()).not.toHaveClass('ws-handle--held')
    expect(set(container)).toBeNull()
  })

  it('moves 16px a press with ← and →, and to its ends with Home and End', async () => {
    windowAt(1440)
    laidOut(BODY)
    const user = userEvent.setup()
    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen />,
    )

    await user.tab()
    expect(handle()).toHaveFocus()

    await user.keyboard('{ArrowLeft}{ArrowLeft}')
    expect(set(container)).toBe(`${String(OWN + 2 * STEP)}px`)
    expect(handle()).toHaveAttribute('aria-valuenow', String(OWN + 2 * STEP))
    await user.keyboard('{ArrowRight}')
    expect(set(container)).toBe(`${String(OWN + STEP)}px`)

    await user.keyboard('{End}')
    expect(set(container)).toBe(`${String(BODY - SHEET_MIN)}px`)
    await user.keyboard('{ArrowLeft}')
    expect(set(container)).toBe(`${String(BODY - SHEET_MIN)}px`)

    await user.keyboard('{Home}')
    expect(set(container)).toBeNull()
    await user.keyboard('{ArrowRight}')
    expect(set(container)).toBeNull()
    // a key that is not the handle's is left alone, and one that is does nothing else: the page does not scroll by it
    await user.keyboard('{ArrowUp}a')
    expect(set(container)).toBeNull()
    expect(fireEvent.keyDown(handle(), { key: 'ArrowUp' })).toBe(true)
    expect(fireEvent.keyDown(handle(), { key: 'End' })).toBe(false)
  })

  it('leaves an arrow held with ⌘, Ctrl or Alt to the browser, whose own it is', () => {
    windowAt(1440)
    laidOut(BODY)
    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen />,
    )

    for (const held of [{ metaKey: true }, { ctrlKey: true }, { altKey: true }]) {
      // not prevented, so ⌘← still goes back a page
      expect(fireEvent.keyDown(handle(), { key: 'ArrowLeft', ...held }), JSON.stringify(held)).toBe(
        true,
      )
    }
    expect(set(container)).toBeNull()
  })

  it('puts the margin back at its own width on a double click', async () => {
    windowAt(1440)
    laidOut(BODY)
    const user = userEvent.setup()
    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen screen="rules" />,
    )
    draw(EDGE, EDGE - 200)
    expect(set(container)).toBe(`${String(OWN + 200)}px`)

    await user.dblClick(handle())

    expect(set(container)).toBeNull()
    expect(handle()).toHaveAttribute('aria-valuenow', String(OWN))
    expect(localStorage.getItem('pp-margin-rules')).toBeNull()
  })

  it('remembers the width per screen in this browser, once the handle is let go', () => {
    windowAt(1440)
    laidOut(BODY)
    const { container, unmount } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen screen="rules" />,
    )

    fireEvent.pointerDown(handle(), { pointerId: 1, button: 0, clientX: EDGE })
    fireEvent.pointerMove(handle(), { pointerId: 1, clientX: EDGE - 180 })
    expect(localStorage.getItem('pp-margin-rules')).toBeNull()
    fireEvent.pointerUp(handle(), { pointerId: 1, clientX: EDGE - 180 })
    expect(localStorage.getItem('pp-margin-rules')).toBe(String(OWN + 180))
    expect(set(container)).toBe(`${String(OWN + 180)}px`)
    unmount()

    // the Rules screen opens again at the width it was left at
    const again = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen screen="rules" />,
    )
    expect(set(again.container)).toBe(`${String(OWN + 180)}px`)
    expect(handle()).toHaveAttribute('aria-valuenow', String(OWN + 180))
    again.unmount()

    // and another screen's margin is its own
    const cases = render(
      <SplitView main={<p>The cases</p>} side={<p>The trace</p>} sideOpen wide screen="cases" />,
    )
    expect(set(cases.container)).toBeNull()
    expect(handle()).toHaveAttribute('aria-valuenow', String(OWN_WIDE))
  })

  // the spec, section 08 (v3.10): a screen is drawn once, so the remembered width stands before anything is measured
  it('draws a remembered width from the first frame, before the body is measured', () => {
    windowAt(1440)
    localStorage.setItem('pp-margin-rules', '520')

    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen screen="rules" />,
    )

    expect(set(container)).toBe('520px')
    expect(screen.queryByRole('separator')).toBeNull()
  })

  it('holds a remembered width to what the window allows, and gives it back when the window grows', () => {
    windowAt(1440)
    localStorage.setItem('pp-margin-rules', '600')
    const layout = laidOut(1000)
    render(<SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen screen="rules" />)

    expect(handle()).toHaveAttribute('aria-valuemax', String(1000 - SHEET_MIN))
    expect(handle()).toHaveAttribute('aria-valuenow', String(1000 - SHEET_MIN))

    layout.resize(BODY)
    expect(handle()).toHaveAttribute('aria-valuemax', String(BODY - SHEET_MIN))
    expect(handle()).toHaveAttribute('aria-valuenow', '600')
  })

  it.each(['wide', '-40', '0', ''])('takes a stored %j for no width at all', (stored) => {
    windowAt(1440)
    laidOut(BODY)
    localStorage.setItem('pp-margin-rules', stored)

    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen screen="rules" />,
    )

    expect(set(container)).toBeNull()
    expect(handle()).toHaveAttribute('aria-valuenow', String(OWN))
  })

  it('still widens the margin, for this visit, in a browser that refuses its storage', () => {
    windowAt(1440)
    laidOut(BODY)
    const refused = () => {
      throw new DOMException('The operation is insecure.', 'SecurityError')
    }
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(refused)
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(refused)
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(refused)
    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen screen="rules" />,
    )

    draw(EDGE, EDGE - 100)

    expect(set(container)).toBe(`${String(OWN + 100)}px`)
  })

  it('has no handle and no set width where the margin is a drawer, on a phone, or closed', () => {
    localStorage.setItem('pp-margin-rules', '520')
    laidOut(BODY)

    for (const width of [1199, 720, 390]) {
      windowAt(width)
      const { container, unmount } = render(
        <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen screen="rules" />,
      )
      expect(screen.queryByRole('separator'), String(width)).toBeNull()
      expect(set(container), String(width)).toBeNull()
      unmount()
    }

    windowAt(1440)
    const { container } = render(
      <SplitView main={<p>The table</p>} side={<p>The rule</p>} sideOpen={false} screen="rules" />,
    )
    expect(screen.queryByRole('separator')).toBeNull()
    expect(set(container)).toBeNull()
    // the width waits for the margin to stand beside the sheet again
    expect(localStorage.getItem('pp-margin-rules')).toBe('520')
  })

  it("draws a set width between the margin's own and what leaves the sheet its floor", () => {
    expect(rule(css, '.ws-body')['--margin-own']).toBe('var(--margin-w)')
    expect(rule(css, '.ws-body--wide-margin')['--margin-own']).toBe('var(--margin-w-wide)')
    expect(rule(css, '.ws-body--set')['grid-template-columns']?.replace(/\s+/g, ' ')).toBe(
      'minmax(0, 1fr) clamp(var(--margin-own), var(--margin-set, var(--margin-own)), calc(100% - var(--sheet-min)))',
    )
    // the sheet's floor leaves the widest margin room to grow at the narrowest window that has a handle
    expect(1200 - px('rail-w') - SHEET_MIN).toBeGreaterThan(OWN_WIDE)
  })

  it('draws the handle as the spec writes it: nothing at rest, the edge under the pointer or the focus', () => {
    const spec = specRules("/* The margin's handle", '.sec {')

    expect(spec.map(([selector]) => selector)).toContain('.ws-handle:focus-visible::before')
    expect(unported(css, spec)).toEqual([])
    expect(rule(css, '.ws-handle::before').opacity).toBe('0')
  })

  it('leaves the drawer and the phone their one column: no handle, and the set width after neither', () => {
    const narrow = media(css, '(max-width: 1199px)')

    expect(rule(narrow, '.ws-handle').display).toBe('none')
    // equal selectors: the later rule wins, so the drawer's one column must come after the set width's two
    expect(css.indexOf('.ws-body--set {')).toBeGreaterThan(-1)
    expect(css.indexOf('.ws-body--set {')).toBeLessThan(css.indexOf('@media (max-width: 1199px)'))
  })
})
