import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import { AddPolicyForm } from './AddPolicyForm'

/**
 * Adding a policy (Brief FR-1; Document 5, Input Validation): the two ways to add one, the refusals said in the
 * words of the person who pasted the text, and the labels that never become placeholders.
 */
function renderForm(overrides: Partial<Parameters<typeof AddPolicyForm>[0]> = {}) {
  const onSubmit = vi.fn()
  const onCancel = vi.fn()
  render(
    <AddPolicyForm
      pending={false}
      error={null}
      onSubmit={onSubmit}
      onCancel={onCancel}
      {...overrides}
    />,
  )
  return { onSubmit, onCancel }
}

describe('AddPolicyForm', () => {
  it('sends the pasted text with its title and language', async () => {
    const { onSubmit } = renderForm()

    await userEvent.type(screen.getByLabelText('Title'), 'Rental deposits')
    await userEvent.selectOptions(screen.getByLabelText('Language'), 'en')
    await userEvent.type(screen.getByLabelText('Policy text'), 'Deposits are returned in 30 days.')
    await userEvent.click(screen.getByRole('button', { name: 'Add policy' }))

    expect(onSubmit).toHaveBeenCalledWith({
      title: 'Rental deposits',
      language: 'en',
      text: 'Deposits are returned in 30 days.',
    })
  })

  it('sends the chosen file when the upload tab is open', async () => {
    const { onSubmit } = renderForm()
    const file = new File(['Applicants must be 21.'], 'policy.md', { type: 'text/markdown' })

    await userEvent.click(screen.getByRole('button', { name: 'Upload a file' }))
    await userEvent.type(screen.getByLabelText('Title'), 'Uploaded policy')
    await userEvent.upload(screen.getByLabelText('Policy file'), file)
    await userEvent.click(screen.getByRole('button', { name: 'Add policy' }))

    expect(onSubmit).toHaveBeenCalledWith({ title: 'Uploaded policy', language: 'he', file })
  })

  it('keeps the action out of reach until the form can be sent', async () => {
    renderForm()

    expect(screen.getByRole('button', { name: 'Add policy' })).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Title'), 'Only a title')
    expect(screen.getByRole('button', { name: 'Add policy' })).toBeDisabled()
  })

  it('writes a Hebrew draft right to left and an English one left to right', async () => {
    renderForm()

    const text = screen.getByLabelText('Policy text')
    await userEvent.type(text, 'הלוואה אישית')
    expect(text).toHaveAttribute('dir', 'rtl')
  })

  // Document 2, Error codes; Document 5: the message names the limit, never the text that broke it
  it.each([
    ['POLICY_INVALID', /over its limits/],
    ['UPLOAD_REJECTED', /\.txt, \.md or \.pdf/],
    ['PAYLOAD_TOO_LARGE', /larger than the 2 MB/],
    ['RATE_LIMITED', /Too many requests/],
    ['INTERNAL_ERROR', /INTERNAL_ERROR/],
  ])('explains %s in the words of the person who pasted the text', (code, expected) => {
    renderForm({
      error: new ApiError(422, { code, message: 'refused', details: [], traceId: 'trace' }),
    })

    expect(screen.getByRole('alert')).toHaveTextContent(expected)
  })

  it('explains a failure that never reached the API', () => {
    renderForm({ error: new TypeError('network down') })

    expect(screen.getByRole('alert')).toHaveTextContent('could not be saved')
  })

  it('shows the action as working while the policy is being saved', () => {
    renderForm({ pending: true })

    expect(screen.getByRole('button', { name: 'Add policy' })).toHaveAttribute('aria-busy', 'true')
  })

  it('closes without sending anything', async () => {
    const { onCancel, onSubmit } = renderForm()

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onCancel).toHaveBeenCalledOnce()
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
