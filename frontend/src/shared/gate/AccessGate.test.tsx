import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AccessGate } from './AccessGate'

describe('AccessGate', () => {
  it('renders the product name, the code field and a disabled button', () => {
    render(<AccessGate />)

    expect(screen.getByRole('heading', { level: 1, name: 'PolicyPilot' })).toBeInTheDocument()
    expect(screen.getByLabelText('Access code')).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Enter' })).toBeDisabled()
  })

  it('enables the button once a code is typed', async () => {
    const user = userEvent.setup()
    render(<AccessGate />)

    await user.type(screen.getByLabelText('Access code'), 'demo1234')

    expect(screen.getByRole('button', { name: 'Enter' })).toBeEnabled()
  })

  it('stays on the gate when the form is submitted (the exchange arrives on day 4)', async () => {
    const user = userEvent.setup()
    render(<AccessGate />)

    await user.type(screen.getByLabelText('Access code'), 'demo1234')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(screen.getByRole('heading', { level: 1, name: 'PolicyPilot' })).toBeInTheDocument()
    expect(screen.getByLabelText('Access code')).toHaveValue('demo1234')
  })
})
