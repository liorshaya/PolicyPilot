import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { AUTH_CODE_URL, AUTH_SESSION_URL } from './api/auth'
import { budgetSpent } from './test/fixtures/budget'
import { server } from './test/msw/server'
import { App } from './App'
import { SCRIPTED_CHANGE_REQUEST, SCRIPTED_QUESTIONS } from './features/demo/steps'

const BASE = 'http://localhost:8080/api/v1'

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
  // the theme lives on <html> and in the browser's storage, and the screen in the address bar, outside what cleanup()
  // resets
  afterEach(() => {
    localStorage.removeItem('pp-theme')
    document.documentElement.removeAttribute('data-theme')
    window.location.hash = ''
  })

  // The Register spec, section 12: "Light by default ...; dark is a toggle remembered per browser". Expected: the
  // data-theme attribute on <html> reads light when nothing is stored, and dark once pp-theme holds a dark choice
  it('starts in the light theme and remembers a dark choice per browser', () => {
    const first = renderApp()
    expect(document.documentElement).toHaveAttribute('data-theme', 'light')
    first.unmount()

    localStorage.setItem('pp-theme', 'dark')
    renderApp()

    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
  })

  it('shows the access gate on first load', async () => {
    renderApp()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Enter the workspace' }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Access code')).toBeInTheDocument()
  })

  // Document 2, GET /auth/session (2026-10-01); the spec (v3.8), section 11, Gate: on load the paper alone is drawn while
  // the API is asked, and a session that holds opens the workspace without the gate
  it('draws the paper alone while it asks whether the session holds', () => {
    renderApp()

    expect(screen.queryByLabelText('Access code')).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Workspace' })).not.toBeInTheDocument()
    expect(document.querySelector('main.gate[aria-busy="true"]')).not.toBeNull()
  })

  it('opens the workspace on load while the session holds, without the code', async () => {
    server.use(http.get(AUTH_SESSION_URL, () => new HttpResponse(null, { status: 204 })))
    renderApp()

    expect(await screen.findByRole('navigation', { name: 'Workspace' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Access code')).not.toBeInTheDocument()
  })

  // Document 2, DELETE /auth/session: Leave ends the session in this browser, then the gate
  it('ends the session when the visitor leaves, and shows the gate', async () => {
    let left = false
    server.use(
      http.get(AUTH_SESSION_URL, () => new HttpResponse(null, { status: 204 })),
      http.delete(AUTH_SESSION_URL, () => {
        left = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    renderApp()
    const rail = await screen.findByRole('navigation', { name: 'Workspace' })

    await user.click(within(rail).getByRole('button', { name: 'Leave' }))

    expect(await screen.findByLabelText('Access code')).toBeInTheDocument()
    expect(left).toBe(true)
    // the visitor chose to leave: the gate says nothing about it
    expect(screen.queryByRole('note', { name: 'Session' })).not.toBeInTheDocument()
  })

  // the spec (v3.9), section 11, Gate: a session that ends while the workspace is open, any call answered 401
  // SESSION_INVALID (its cookie expired or was tampered with), brings the gate back with the system's note; every
  // screen's Try again had answered the same 401 again
  it('brings the gate back with a note when the session ends while the workspace is open', async () => {
    server.use(
      http.get(AUTH_SESSION_URL, () => new HttpResponse(null, { status: 204 })),
      http.get(`${BASE}/policies`, () =>
        HttpResponse.json(
          {
            code: 'SESSION_INVALID',
            message: 'A valid session is required.',
            details: [],
            traceId: 't',
          },
          { status: 401 },
        ),
      ),
    )
    renderApp()

    expect(await screen.findByLabelText('Access code')).toBeInTheDocument()
    expect(screen.getByRole('note', { name: 'Session' })).toHaveTextContent(
      'Your session has ended. Enter the code again.',
    )
    expect(screen.queryByRole('navigation', { name: 'Workspace' })).not.toBeInTheDocument()
  })

  // the spec (v3.9), section 11, Gate: Leave, or the end of a session, drops all the workspace held, which was its
  // sandbox's; a new session is a new sandbox (Document 5), which the last one's run of the cases is not
  it("drops what the workspace held when the session closes, so the next one shows nothing of the last one's", async () => {
    server.use(
      http.get(AUTH_SESSION_URL, () => new HttpResponse(null, { status: 204 })),
      http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })),
    )
    const user = userEvent.setup()
    renderApp()
    const rail = await screen.findByRole('navigation', { name: 'Workspace' })
    await user.click(within(rail).getByRole('button', { name: /^Cases/ }))
    await user.click((await screen.findAllByRole('button', { name: 'Run 200 cases' }))[0]!)
    expect(await screen.findByRole('button', { name: '17' })).toBeInTheDocument()

    await user.click(
      within(screen.getByRole('navigation', { name: 'Workspace' })).getByRole('button', {
        name: 'Leave',
      }),
    )
    await user.type(await screen.findByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(await screen.findByRole('heading', { level: 1, name: /^Cases/ })).toBeInTheDocument()
    expect(await screen.findByText('Published v1')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '17' })).not.toBeInTheDocument()
  })

  it('opens the workspace once the code is accepted', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()

    await user.type(await screen.findByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(await screen.findByRole('navigation', { name: 'Workspace' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Policies' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Access code')).not.toBeInTheDocument()
  })

  // Document 2, API Surface: GET /system/provider is "shown in the UI header"; the Register (section 08) shows it as the
  // rail's provider line, with the models in the legend behind Help. Expected: the provider the API answers, in the rail
  it('shows the active provider in the rail once the code is accepted, and its models behind Help', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()

    await user.type(await screen.findByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    const nav = await screen.findByRole('navigation', { name: 'Workspace' })
    expect(await within(nav).findByText('Provider OpenAI · cloud')).toBeVisible()
    await user.click(within(nav).getByRole('button', { name: 'Help' }))
    expect(screen.getByRole('dialog', { name: 'Help' })).toHaveTextContent('gpt-5.6-terra')
  })

  it('lists the screens of the workspace, every one of them built', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()

    await user.type(await screen.findByLabelText('Access code'), 'qwertyui')
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

  // The spec, section 08: the spent budget "sits under the workspace header of every screen that calls a model";
  // the assistant says so under its composer instead (section 09), and the audit log calls none
  it("says the day's budget is spent under the header of every screen that calls a model", async () => {
    server.use(
      http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })),
      http.get('http://localhost:8080/api/v1/system/budget', () => HttpResponse.json(budgetSpent)),
    )
    const user = userEvent.setup()
    renderApp()
    await user.type(await screen.findByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))
    const nav = await screen.findByRole('navigation', { name: 'Workspace' })

    for (const name of ['Policies', 'Rules', 'Cases', 'Change']) {
      await user.click(within(nav).getByRole('button', { name: new RegExp(`^${name}`) }))
      await screen.findByRole('heading', { level: 1, name })
      const note = await screen.findByRole('note', { name: 'Budget' })
      // under the header, before the body
      expect(note.closest('.budget-note')!.previousElementSibling).toHaveClass('ws-header')
    }
    await user.click(within(nav).getByRole('button', { name: /Assistant/ }))
    await screen.findByRole('heading', { level: 1, name: 'Assistant' })
    expect(await screen.findAllByRole('note', { name: 'Budget' })).toHaveLength(1)
    await user.click(within(nav).getByRole('button', { name: /Audit log/ }))
    await screen.findByRole('heading', { level: 1, name: 'Audit log' })
    expect(screen.queryByRole('note', { name: 'Budget' })).not.toBeInTheDocument()
  })

  // Brief FR-23; Document 2: the panel's steps "pre-fill the inputs and call the same API the regular screens
  // use". Expected: step 3 opens the assistant on the first of the brief's three scripted questions
  it('runs a scripted step on the screen that step belongs to', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    renderApp()
    await user.type(await screen.findByLabelText('Access code'), 'qwertyui')
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
    await user.type(await screen.findByLabelText('Access code'), 'qwertyui')
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
    await user.type(await screen.findByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))
    await screen.findByRole('navigation', { name: 'Workspace' })
    await user.click(screen.getByRole('button', { name: /guided demo/i }))

    await user.click(screen.getAllByRole('button', { name: 'Run' })[1]!)

    expect(await screen.findByRole('heading', { level: 1, name: 'Cases' })).toBeInTheDocument()
  })
})
