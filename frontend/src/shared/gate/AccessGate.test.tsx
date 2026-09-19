import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { AUTH_CODE_URL } from '../../api/auth'
import { server } from '../../test/msw/server'
import { AccessGate } from './AccessGate'

describe('AccessGate', () => {
  it('renders the product name, the code field and a disabled button', () => {
    render(<AccessGate onEntered={vi.fn()} />)

    expect(screen.getByRole('heading', { level: 1, name: 'PolicyPilot' })).toBeInTheDocument()
    expect(screen.getByLabelText('Access code')).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Enter' })).toBeDisabled()
  })

  it('enables the button once a code is typed', async () => {
    const user = userEvent.setup()
    render(<AccessGate onEntered={vi.fn()} />)

    await user.type(screen.getByLabelText('Access code'), 'demo1234')

    expect(screen.getByRole('button', { name: 'Enter' })).toBeEnabled()
  })

  it('enters when the API accepts the code', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const onEntered = vi.fn()
    const user = userEvent.setup()
    render(<AccessGate onEntered={onEntered} />)

    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(onEntered).toHaveBeenCalledOnce()
  })

  it('says the code is not valid and keeps the field when the API refuses it', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 401 })))
    const onEntered = vi.fn()
    const user = userEvent.setup()
    render(<AccessGate onEntered={onEntered} />)

    await user.type(screen.getByLabelText('Access code'), 'wrongone')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('That code is not valid.')
    expect(screen.getByLabelText('Access code')).toHaveValue('wrongone')
    expect(onEntered).not.toHaveBeenCalled()
  })

  it('tells a locked-out visitor when to try again', async () => {
    server.use(
      http.post(
        AUTH_CODE_URL,
        () => new HttpResponse(null, { status: 429, headers: { 'Retry-After': '840' } }),
      ),
    )
    const user = userEvent.setup()
    render(<AccessGate onEntered={vi.fn()} />)

    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 14 minutes.')
  })

  it('never repeats the typed code in its message', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 401 })))
    const user = userEvent.setup()
    render(<AccessGate onEntered={vi.fn()} />)

    await user.type(screen.getByLabelText('Access code'), 'secretxy')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(await screen.findByRole('alert')).not.toHaveTextContent('secretxy')
  })
})
