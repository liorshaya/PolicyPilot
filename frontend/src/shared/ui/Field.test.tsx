import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Field } from './Field'

/**
 * A field of the product (the spec, section 05): the label above, the hint under it, the error in place of the hint
 * with its glyph, and a counter that is always visible and turns amber at 90% of the limit the API states.
 */
describe('Field', () => {
  it('shows the error in place of the hint, with its glyph, as an alert', () => {
    render(
      <Field
        label="Policy file"
        htmlFor="file"
        hint="A .txt, .md or .pdf"
        error="The file must be under 2 MB."
      >
        <input id="file" className="input" />
      </Field>,
    )
    const error = screen.getByRole('alert')

    expect(error).toHaveClass('field__error')
    expect(error).toHaveTextContent('The file must be under 2 MB.')
    expect(error.querySelector('svg')).toHaveAttribute('data-icon', 'warn')
    expect(screen.queryByText('A .txt, .md or .pdf')).not.toBeInTheDocument()
  })

  it('counts toward the limit beside the hint, and turns amber at 90% of it', () => {
    const { rerender } = render(
      <Field
        label="What should change"
        htmlFor="request"
        hint="In the policy's own terms."
        counter={{ value: 41, max: 1000 }}
      >
        <textarea id="request" className="textarea" />
      </Field>,
    )

    expect(screen.getByText('41 / 1,000')).toHaveClass('counter')
    expect(screen.getByText('41 / 1,000')).not.toHaveClass('counter--near')

    rerender(
      <Field
        label="What should change"
        htmlFor="request"
        hint="In the policy's own terms."
        counter={{ value: 912, max: 1000 }}
      >
        <textarea id="request" className="textarea" />
      </Field>,
    )

    expect(screen.getByText('912 / 1,000')).toHaveClass('counter', 'counter--near')
  })
})
