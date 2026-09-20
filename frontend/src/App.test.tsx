import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { AUTH_CODE_URL } from './api/auth'
import { server } from './test/msw/server'
import { App } from './App'

/** The application: the gate, then the workspace with its sidebar (Document 2, Frontend Architecture). */
function renderApp() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )
}

describe('App', () => {
  it('shows the access gate on first load', () => {
    renderApp()

    expect(
      screen.getByRole('heading', { level: 1, name: 'Enter the workspace' }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Access code')).toBeInTheDocument()
  })

  it('opens the workspace once the code is accepted', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()

    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(await screen.findByRole('navigation', { name: 'Workspace' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Policies' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Access code')).not.toBeInTheDocument()
  })

  it('lists the screens of the workspace and marks the ones still to come', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()

    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    const nav = await screen.findByRole('navigation', { name: 'Workspace' })
    expect(nav).toHaveTextContent('Policies')
    expect(nav).toHaveTextContent('Rules')
    expect(nav).toHaveTextContent('Cases')
    expect(screen.getByRole('button', { name: /Assistant/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Audit log/ })).toBeDisabled()
  })
})
