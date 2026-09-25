import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { AUTH_CODE_URL } from './api/auth'
import { server } from './test/msw/server'
import { App } from './App'
import { SCRIPTED_CHANGE_REQUEST, SCRIPTED_QUESTIONS } from './features/demo/steps'

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

  // Document 2, API Surface: GET /system/provider is "shown in the UI header". Expected: the provider the API
  // answers, named at the top of the sidebar beside the logo, on the workspace's first screen
  it('shows the active provider in the header once the code is accepted', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()

    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    const nav = await screen.findByRole('navigation', { name: 'Workspace' })
    const badge = await within(nav).findByRole('region', { name: 'Model provider' })
    expect(await within(badge).findByText('OpenAI')).toBeVisible()
    expect(within(badge).getByText('gpt-5.6-terra')).toBeVisible()
  })

  it('lists the screens of the workspace, every one of them built', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()

    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    const nav = await screen.findByRole('navigation', { name: 'Workspace' })
    expect(nav).toHaveTextContent('Policies')
    expect(nav).toHaveTextContent('Rules')
    expect(nav).toHaveTextContent('Cases')
    // the assistant arrived on day 9, the change screen and the audit log on day 14
    expect(screen.getByRole('button', { name: /Assistant/ })).toBeEnabled()
    expect(within(nav).getByRole('button', { name: /^Change/ })).toBeEnabled()
    expect(screen.getByRole('button', { name: /Audit log/ })).toBeEnabled()
    expect(within(nav).queryByText('Soon')).not.toBeInTheDocument()
    // and each of the two screens of day 14 opens as itself
    await user.click(within(nav).getByRole('button', { name: /^Change/ }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Change' })).toBeVisible()
    await user.click(within(nav).getByRole('button', { name: /Audit log/ }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Audit log' })).toBeVisible()
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

  // Brief, demo step 4: "Type: Raise the minimum monthly income to 9,000". Expected: the panel opens the change
  // screen with the labeled request CR-1 filled in, and the presenter proposes it
  it('runs step 4 on the change screen, with the scripted request filled in', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()
    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))
    await screen.findByRole('navigation', { name: 'Workspace' })

    await user.click(screen.getByRole('button', { name: /guided demo/i }))
    await user.click(screen.getAllByRole('button', { name: 'Run' })[3]!)

    expect(await screen.findByRole('heading', { level: 1, name: 'Change' })).toBeInTheDocument()
    expect(await screen.findByLabelText('What should change')).toHaveValue(SCRIPTED_CHANGE_REQUEST)
    expect(window.location.hash).toBe('#/change')
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
