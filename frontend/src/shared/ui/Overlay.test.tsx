import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { Dialog, Popover, Toast } from './Overlay'

const css = stylesheet('shared/ui/Overlay.css')

/**
 * The overlays (the spec, section 08): a dialog only for the irreversible, asked as a question; a popover placed from
 * the control that opened it; a toast that confirms, offers the next step and goes, and an error toast that stays.
 */
describe('Dialog', () => {
  it('exists only with a question for its title, and names what cannot be undone', () => {
    const { rerender } = render(
      <Dialog
        title="Publish version 2?"
        cannotUndo="Publishing cannot be undone; a later change creates version 3."
        actions={<button type="button">Publish version 2</button>}
      >
        Version 2 becomes the version that decides cases.
      </Dialog>,
    )

    const dialog = screen.getByRole('dialog', { name: 'Publish version 2?' })
    expect(dialog).toHaveTextContent(
      'Publishing cannot be undone; a later change creates version 3.',
    )

    rerender(
      <Dialog
        title={'Publish version 2' as `${string}?`}
        cannotUndo="Publishing cannot be undone."
        actions={null}
      >
        Version 2 becomes the version that decides cases.
      </Dialog>,
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('Popover', () => {
  it('opens at the edge of its anchor: under it in the upper half of the window, over it in the lower half', () => {
    vi.stubGlobal('innerHeight', 900)
    const anchor = document.createElement('button')
    document.body.append(anchor)
    const at = (top: number) =>
      vi.spyOn(anchor, 'getBoundingClientRect').mockReturnValue(new DOMRect(24, top, 100, 28))

    at(100)
    const { rerender } = render(
      <Popover anchor={anchor} label="Help" onClose={vi.fn()}>
        The legend
      </Popover>,
    )
    const popover = screen.getByRole('dialog', { name: 'Help' })

    expect(popover).toHaveStyle({ left: '24px', top: '128px' })
    expect(popover).toHaveAttribute('data-side', 'below')

    at(820)
    rerender(
      <Popover anchor={anchor} label="Help" onClose={vi.fn()}>
        The legend again
      </Popover>,
    )

    expect(screen.getByRole('dialog', { name: 'Help' })).toHaveStyle({
      left: '24px',
      bottom: '80px',
    })
    expect(screen.getByRole('dialog', { name: 'Help' })).toHaveAttribute('data-side', 'above')
    anchor.remove()
    vi.unstubAllGlobals()
  })

  // The phone's menu (the spec, section 10) opens from a button at the window's end edge, so it lines up with that edge
  it("lines up with its anchor's end edge when asked, for a control at the window's end", () => {
    vi.stubGlobal('innerHeight', 844)
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(390)
    const anchor = document.createElement('button')
    document.body.append(anchor)
    vi.spyOn(anchor, 'getBoundingClientRect').mockReturnValue(new DOMRect(340, 10, 36, 28))

    render(
      <Popover anchor={anchor} label="Menu" align="end" onClose={vi.fn()}>
        The menu
      </Popover>,
    )

    const popover = screen.getByRole('dialog', { name: 'Menu' })
    // 390 − (340 + 36): the popover's end edge is the button's
    expect(popover).toHaveStyle({ right: '14px', top: '38px' })
    expect(popover.style.left).toBe('')
    anchor.remove()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  // the spec (v3.9), section 08: a popover keeps inside the window, a token's gap from its edge; on a 320px phone the
  // menu, 320px wide and lined up with its button's end, started 14px before the window
  it('keeps inside the window, as wide as the room beside its anchor leaves it', () => {
    vi.stubGlobal('innerHeight', 700)
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(320)
    const anchor = document.createElement('button')
    document.body.append(anchor)
    vi.spyOn(anchor, 'getBoundingClientRect').mockReturnValue(new DOMRect(270, 10, 36, 28))

    render(
      <Popover anchor={anchor} label="Menu" align="end" onClose={vi.fn()}>
        The menu
      </Popover>,
    )

    const popover = screen.getByRole('dialog', { name: 'Menu' })
    // 320 − (270 + 36): the room is all the window holds before the button's end edge
    expect(popover).toHaveStyle({ right: '14px' })
    expect(popover.style.getPropertyValue('--popover-offset')).toBe('14px')
    expect(rule(css, '.popover[data-side]')['max-width']).toBe(
      'calc(100vw - var(--popover-offset, 0px) - var(--s-2))',
    )
    anchor.remove()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('closes on Escape', async () => {
    const onClose = vi.fn()
    const anchor = document.createElement('button')
    document.body.append(anchor)
    render(
      <Popover anchor={anchor} label="Help" onClose={onClose}>
        The legend
      </Popover>,
    )

    await userEvent.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalledOnce()
    anchor.remove()
  })
})

describe('Toast', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('confirms, offers the next step, and goes once its time is up', () => {
    vi.useFakeTimers()
    const onClose = vi.fn()
    const next = vi.fn()
    render(
      <Toast
        action={{ label: 'Open the audit log', onClick: next }}
        lifetimeMs={5000}
        onClose={onClose}
      >
        Version 2 published.
      </Toast>,
    )

    expect(screen.getByRole('status')).toHaveTextContent('Version 2 published.')
    act(() => {
      screen.getByRole('button', { name: 'Open the audit log' }).click()
    })
    expect(next).toHaveBeenCalledOnce()

    act(() => {
      vi.advanceTimersByTime(5000)
    })
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('keeps an error toast until it is closed', () => {
    vi.useFakeTimers()
    const onClose = vi.fn()
    render(
      <Toast tone="error" lifetimeMs={5000} onClose={onClose}>
        The model did not answer in time. Nothing was stored.
      </Toast>,
    )

    act(() => {
      vi.advanceTimersByTime(60_000)
    })
    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveClass('toast', 'toast--error')

    act(() => {
      screen.getByRole('button', { name: 'Close' }).click()
    })
    expect(onClose).toHaveBeenCalledOnce()
  })
})
