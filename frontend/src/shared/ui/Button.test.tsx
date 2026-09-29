import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { resolved, rule, stylesheet, token } from '../../test/css'
import { Button } from './Button'

/**
 * The control of the Register (the spec, section 05): one primary per screen, drawn in ink on paper and in paper on ink
 * in the dark theme; a bordered secondary, a quiet one of text alone, a danger in red text; busy keeps the width; no
 * icon on Run, Publish or Ask.
 */
const css = stylesheet('shared/ui/Button.css')

describe('Button', () => {
  it('draws the primary in ink on paper, and in paper on ink in the dark theme', () => {
    render(<Button variant="primary">Publish version 2</Button>)
    const primary = rule(css, '.btn--primary')

    expect(screen.getByRole('button', { name: 'Publish version 2' })).toHaveClass(
      'btn',
      'btn--primary',
    )
    expect(resolved(primary.background!, 'light')).toBe(token('ink', 'light'))
    expect(resolved(primary.color!, 'light')).toBe(token('paper', 'light'))
    expect(resolved(primary.background!, 'dark')).toBe(token('ink', 'dark'))
    expect(resolved(primary.color!, 'dark')).toBe(token('paper', 'dark'))
  })

  it('draws the secondary bordered, the quiet as text alone and the danger in red text', () => {
    render(
      <>
        <Button>Run 200 cases</Button>
        <Button variant="quiet">Open the rules</Button>
        <Button variant="danger">Reject</Button>
      </>,
    )

    expect(screen.getByRole('button', { name: 'Run 200 cases' })).toHaveClass('btn--secondary')
    expect(rule(css, '.btn--secondary')['border-color']).toBe('var(--border)')
    expect(screen.getByRole('button', { name: 'Open the rules' })).toHaveClass('btn--quiet')
    expect(rule(css, '.btn--quiet')).toStrictEqual({ color: 'var(--ink-2)' })
    expect(screen.getByRole('button', { name: 'Reject' })).toHaveClass('btn--danger')
    expect(rule(css, '.btn--danger').color).toBe('var(--decline-text)')
  })

  it('draws an action the spec writes as a link in the colour of a link, with no box of its own', () => {
    // the spec draws "Show every comparison", "Declines only" and "Open the policy" as links; each is an action here
    render(<Button variant="link">Show every comparison</Button>)

    expect(screen.getByRole('button', { name: 'Show every comparison' })).toHaveClass(
      'btn',
      'btn--link',
    )
    expect(rule(css, '.btn--link')).toMatchObject({
      height: 'auto',
      padding: '0',
      border: '0',
      color: 'var(--accent-text)',
    })
  })

  it('keeps its width while busy: the label stays, hidden under the spinner, and a click does nothing', async () => {
    const onClick = vi.fn()
    render(
      <Button variant="primary" busy onClick={onClick}>
        Publish version 2
      </Button>,
    )
    const button = screen.getByRole('button', { name: 'Publish version 2' })

    await userEvent.click(button)

    expect(button).toHaveClass('btn--busy')
    expect(button).toHaveAttribute('aria-busy', 'true')
    expect(button).toHaveTextContent('Publish version 2')
    expect(rule(css, '.btn--busy').color).toBe('transparent !important')
    expect(rule(css, '.btn--busy::after').animation).toBe('spin 0.8s linear infinite')
    expect(onClick).not.toHaveBeenCalled()
  })

  // the spec, section 05 (v3.5): a busy button keeps its face while it waits, disabled or not; the owner found the
  // primary of Generate rules going pale under a paper spinner while it was busy and disabled at once
  it('keeps its face while busy and disabled: the primary stays ink under the paper spinner, a danger takes the ink spinner', () => {
    render(
      <Button variant="primary" busy disabled>
        Generate rules
      </Button>,
    )
    const button = screen.getByRole('button', { name: 'Generate rules' })

    expect(button).toBeDisabled()
    expect(button).toHaveClass('btn--primary', 'btn--busy')
    expect(rule(css, '.btn--primary.btn--busy[disabled]')).toMatchObject({
      background: 'var(--ink)',
      'border-color': 'var(--ink)',
    })
    expect(rule(css, '.btn--busy[disabled]')).toMatchObject({ opacity: '1', cursor: 'progress' })
    expect(resolved(rule(css, '.btn--busy::after').border!, 'light')).toBe(
      `2px solid ${token('paper', 'light')}`,
    )
    expect(rule(css, '.btn--danger.btn--busy::after')['border-color']).toBe('var(--ink-2)')
    expect(rule(css, '.btn--link.btn--busy::after')['border-color']).toBe('var(--ink-2)')
  })

  it('shows no icon on Run, Publish or Ask, and shows one where it adds meaning', () => {
    render(
      <>
        <Button icon="plus">Run 200 cases</Button>
        <Button icon="plus">Publish version 2</Button>
        <Button icon="plus">Ask</Button>
        <Button icon="plus">Add policy</Button>
      </>,
    )

    for (const name of ['Run 200 cases', 'Publish version 2', 'Ask']) {
      expect(screen.getByRole('button', { name }).querySelector('svg')).toBeNull()
    }
    expect(screen.getByRole('button', { name: 'Add policy' }).querySelector('svg')).toHaveAttribute(
      'data-icon',
      'plus',
    )
  })
})
