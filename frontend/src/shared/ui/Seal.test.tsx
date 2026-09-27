import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { Seal } from './Seal'

/**
 * A person's approval (the spec, section 04): one rule with a second rule inside it, mono, the only capitals in the
 * product, landing with the one signature motion when a person's authority becomes a fact.
 */
const css = stylesheet('shared/ui/Seal.css')

/** jsdom has no matchMedia; a test says which way the reader's motion preference goes. */
function prefersReducedMotion(reduce: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: reduce && query.includes('reduce'),
    media: query,
  }))
}

describe('Seal', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('writes the kicker in capitals, the one uppercase text, with the line in mono and the name', () => {
    prefersReducedMotion(false)
    render(<Seal kicker="Published" line="v1 · 2026-09-22 14:02" by="Analyst" />)

    expect(screen.getByText('Published')).toHaveClass('seal__kicker')
    expect(rule(css, '.seal__kicker')['text-transform']).toBe('uppercase')
    expect(rule(css, '.seal')['font-family']).toBe('var(--font-mono)')
    expect(screen.getByText('v1 · 2026-09-22 14:02')).toHaveClass('seal__line')
    expect(screen.getByText('by Analyst')).toHaveClass('seal__by')
  })

  it('renders the one-line form when inline, and the rejected one in red', () => {
    prefersReducedMotion(false)
    render(<Seal kicker="Rejected" line="CR-0002 · 09:12 · Analyst" inline />)
    const seal = screen.getByText('Rejected').parentElement!

    expect(seal).toHaveClass('seal', 'seal--inline', 'seal--rejected')
    expect(rule(css, '.seal--inline')['grid-auto-flow']).toBe('column')
  })

  it('stamps once on mount, and drops the stamp when its animation of --t-seal ends', () => {
    prefersReducedMotion(false)
    const { rerender } = render(<Seal kicker="Approved" line="CR-0001 · 16:19" inline stamp />)
    const seal = screen.getByText('Approved').parentElement!

    expect(seal).toHaveClass('seal--stamp')
    expect(rule(css, '.seal--stamp').animation).toBe('stamp var(--t-seal) var(--ease-seal) both')

    act(() => {
      fireEvent.animationEnd(seal)
    })
    rerender(<Seal kicker="Approved" line="CR-0001 · 16:19" inline stamp />)

    expect(seal).not.toHaveClass('seal--stamp')
  })

  it('does not stamp when the reader prefers reduced motion', () => {
    prefersReducedMotion(true)
    render(<Seal kicker="Acknowledged" line="F-6 · 14:02" inline stamp />)

    expect(screen.getByText('Acknowledged').parentElement).not.toHaveClass('seal--stamp')
  })
})
