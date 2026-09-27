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
