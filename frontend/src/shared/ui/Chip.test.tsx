import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { Chip } from './Chip'

/**
 * Three silhouettes (the spec, section 06): a mono rectangle is any identifier, a serif pill is a paragraph of the
 * policy, an outlined glyph is a tool call. A chip that opens something is focusable, so it is a button or a link.
 */
const css = stylesheet('shared/ui/Chip.css')

describe('Chip', () => {
  it('sets an identifier in mono', () => {
    render(<Chip>R-330</Chip>)

    expect(screen.getByText('R-330')).toHaveClass('chip', 'chip--id')
    expect(rule(css, '.chip--id')['font-family']).toBe('var(--font-mono)')
  })

  it('draws a paragraph as the serif pill: ¶, a non-breaking space and its number', () => {
    render(<Chip kind="para">7</Chip>)
    const chip = document.querySelector('.chip--para')!

    expect(chip.querySelector('.pilcrow')).toHaveTextContent('¶')
    expect(chip.textContent).toBe(`¶${String.fromCodePoint(0x00a0)}7`)
    expect(rule(css, '.chip--para')['font-family']).toBe('var(--font-doc-en)')
    expect(rule(css, '.chip--para')['border-radius']).toBe('var(--r-pill)')
  })

  it('draws a tool call as the outlined glyph', () => {
    render(<Chip kind="tool">what-if · has_guarantor=true</Chip>)
    const chip = screen.getByText('what-if · has_guarantor=true')

    expect(chip).toHaveClass('chip--tool')
    expect(chip.querySelector('svg')).toHaveAttribute('data-icon', 'flask')
    expect(rule(css, '.chip--tool').background).toBe('transparent')
  })

  it('makes every chip that opens something a button or a link', () => {
    render(
      <>
        <Chip onClick={vi.fn()}>R-170</Chip>
        <Chip kind="para" href="#paragraph-2">
          2
        </Chip>
        <Chip>v1</Chip>
      </>,
    )

    expect(screen.getByRole('button', { name: 'R-170' })).toHaveClass('chip--id')
    expect(screen.getByRole('link', { name: /^¶\s2$/ })).toHaveClass('chip--para')
    expect(screen.getByText('v1').tagName).toBe('SPAN')
  })

  it('keeps 4px of air on each side of a chip inside Hebrew', () => {
    expect(rule(css, "[dir='rtl'] .chip").margin).toBe('0 4px')
  })
})
