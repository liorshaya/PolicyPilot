import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { AUTH_CODE_URL } from './api/auth'
import { server } from './test/msw/server'
import { App } from './App'
import { SCRIPTED_QUESTIONS } from './features/demo/steps'

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
    // the assistant arrived on day 9 and the change screen on day 14; the audit log is still to come
    expect(screen.getByRole('button', { name: /Assistant/ })).toBeEnabled()
    expect(within(nav).getByRole('button', { name: /^Change/ })).toBeEnabled()
    expect(screen.getByRole('button', { name: /Audit log/ })).toBeDisabled()
  })

  // Brief FR-23; Document 2: the panel's steps "pre-fill the inputs and call the same API the regular screens
  // use". Expected: step 3 opens the assistant on the first of the brief's three scripted questions
  it('runs a scripted step on the screen that step belongs to', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()
    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))
    await screen.findByRole('navigation', { name: 'Workspace' })

    await user.click(screen.getByRole('button', { name: /guided demo/i }))
    await user.click(screen.getAllByRole('button', { name: 'Run' })[2]!)

    expect(await screen.findByRole('heading', { level: 1, name: 'Assistant' })).toBeInTheDocument()
    expect(await screen.findByLabelText(/question/i)).toHaveValue(SCRIPTED_QUESTIONS[0])
    expect(window.location.hash).toBe('#/assistant')
  })

  // Expected: the step the demo is on is the one the panel marks, so a presenter can see where they are
  it('marks the step the workspace is on in the panel', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()
    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))
    await screen.findByRole('navigation', { name: 'Workspace' })
    await user.click(screen.getByRole('button', { name: /guided demo/i }))

    await user.click(screen.getAllByRole('button', { name: 'Run' })[1]!)

    expect(await screen.findByRole('heading', { level: 1, name: 'Cases' })).toBeInTheDocument()
  })
})
