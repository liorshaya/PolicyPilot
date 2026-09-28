import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { Severity } from './Severity'

/**
 * The marks follow the publish gate, not a severity word (the spec, section 06): a square must be acknowledged before
 * publishing (a conflict, an unsupported rule, a gap), a bar is an instruction planted in the text, a triangle may stay
 * open (an ambiguity, a duplicate). The kind's word always stands beside the mark.
 */
const css = stylesheet('shared/ui/Severity.css')

describe('Severity', () => {
  it('draws the square for a conflict, an unsupported rule and a gap', () => {
    render(
      <>
        <Severity kind="conflict" />
        <Severity kind="unsupported" />
        <Severity kind="gap" />
      </>,
    )

    for (const word of ['Conflict', 'Unsupported', 'Gap']) {
      expect(screen.getByText(word)).toHaveClass('sev', 'sev--error')
    }
    expect(rule(css, '.sev--error::before')['border-radius']).toBe('1px')
  })

  it('draws the bar for an instruction in the text', () => {
    render(<Severity kind="injection" />)

    expect(screen.getByText('Instruction in the text')).toHaveClass('sev--injection')
    expect(rule(css, '.sev--injection::before').width).toBe('3px')
  })

  it('draws the triangle for an ambiguity and a duplicate', () => {
    render(
      <>
        <Severity kind="ambiguity" />
        <Severity kind="duplicate" />
      </>,
    )

    expect(screen.getByText('Ambiguity')).toHaveClass('sev--warning')
    expect(screen.getByText('Duplicate')).toHaveClass('sev--warning')
    expect(rule(css, '.sev--warning::before')['clip-path']).toBe(
      'polygon(50% 0, 100% 100%, 0 100%)',
    )
  })

  it("writes the kind's word beside the mark, after the finding's code when it has one", () => {
    render(<Severity kind="conflict" code="F-1" />)

    expect(screen.getByText('F-1 Conflict')).toHaveClass('sev--error')
  })

  // The spec, section 10: under a paragraph of the Policies screen the mark carries the finding's code, "F-3"
  it('writes the code alone beside the mark where the spec does, the kind in its title and for a screen reader', () => {
    render(<Severity kind="ambiguity" code="F-3" brief />)

    const mark = screen.getByTitle('F-3 Ambiguity')
    expect(mark).toHaveClass('sev', 'sev--warning')
    expect(mark.firstChild).toHaveTextContent(/^F-3$/)
    expect(mark.querySelector('.sr-only')).toHaveTextContent(/^Ambiguity$/)
  })
})
