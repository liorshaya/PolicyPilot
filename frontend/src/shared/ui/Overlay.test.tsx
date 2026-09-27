import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Dialog, Popover, Toast } from './Overlay'

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
