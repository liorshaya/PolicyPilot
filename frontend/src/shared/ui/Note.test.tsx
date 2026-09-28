import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { Note } from './Note'

/**
 * What a screen must say in place (the spec, section 08): no fill, a 2px bar (ink, amber or red, dashed for a proposal)
 * and the mark of who speaks.
 */
const css = stylesheet('shared/ui/Note.css')

describe('Note', () => {
  it('draws a bar and the mark of who says it, with no fill, and says an error as an alert', () => {
    const { container } = render(
      <>
        <Note tone="warning">
          Publishing waits: the draft was edited after its review; run the review again.
        </Note>
        <Note tone="proposal">A draft rule set was written from this policy.</Note>
        <Note tone="error">Too many questions in a short time. Wait a minute, then try again.</Note>
      </>,
    )
    const [warning, proposal, error] = [...container.querySelectorAll('.note')]

    expect(warning).toHaveClass('note--warning')
    expect(warning!.querySelector('.actor--system')).not.toBeNull()
    expect(proposal).toHaveClass('note--proposal')
    expect(proposal!.querySelector('.actor--model')).not.toBeNull()
    expect(error).toHaveAttribute('role', 'alert')
    expect(rule(css, '.note').background).toBeUndefined()
    expect(rule(css, '.note--proposal')['border-left-style']).toBe('dashed')
  })

  it('names a note a screen keeps in place, so it is found by what it is about', () => {
    const { getByRole } = render(
      <Note tone="warning" label="Budget">
        Today's model budget is spent until 00:00.
      </Note>,
    )

    expect(getByRole('note', { name: 'Budget' })).toHaveTextContent(
      "Today's model budget is spent until 00:00.",
    )
  })
})
