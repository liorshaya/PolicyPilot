import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { AUTH_CODE_URL } from './api/auth'
import { server } from './test/msw/server'
import { App } from './App'

describe('App', () => {
  it('shows the access gate on first load', () => {
    render(<App />)

    expect(screen.getByRole('heading', { level: 1, name: 'PolicyPilot' })).toBeInTheDocument()
    expect(screen.getByLabelText('Access code')).toBeInTheDocument()
  })

  it('leaves the gate once the code is accepted', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(
      await screen.findByText('You are signed in. The policy workspace opens here.'),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText('Access code')).not.toBeInTheDocument()
  })
})
