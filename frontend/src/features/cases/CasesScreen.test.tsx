import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { aggregates, batch, decision } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { CasesScreen } from './CasesScreen'

/**
 * The case runner against realistic responses (Document 6, Frontend Test Design). The aggregates are those of the
 * 200 seeded cases, and the trace is the one the engine writes for a referred case.
 */

const BASE = 'http://localhost:8080/api/v1'

function renderScreen(onOpenRule: (ruleId: string | null) => void = () => undefined) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <CasesScreen onOpenRule={onOpenRule} />
    </QueryClientProvider>,
  )
}

describe('CasesScreen', () => {
  it('shows what the version has decided before anything is run', async () => {
    renderScreen()

    const approved = (await screen.findByText('Approved')).closest('div')
    expect(within(approved!).getByText('113')).toBeInTheDocument()
    expect(within(approved!).getByText('57%')).toBeInTheDocument()
    expect(
      screen.getByText(`Rules that decided most often, of ${aggregates.decisions} decisions`),
    ).toBeInTheDocument()
    // the list of cases belongs to a run; before one there is nothing to list
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('asks for nothing but the seeded set when the 200 cases are run', async () => {
    const user = userEvent.setup()
    let sent: unknown = null
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json(batch)
      }),
    )
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))

    await waitFor(() => expect(sent).toEqual({ fixtureSet: 'cases-200' }))
    expect(
      await screen.findByText('2 cases decided in this run, each with its own trace'),
    ).toBeInTheDocument()
  })

  it('lists every case of the run with what the engine decided and what it flagged', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))

    const referred = (await screen.findByText('17')).closest('tr')
    expect(within(referred!).getByText('Manual review')).toBeInTheDocument()
    expect(within(referred!).getByText('R-330')).toBeInTheDocument()
    const approved = screen.getByText('18').closest('tr')
    expect(within(approved!).getByText('Approved')).toBeInTheDocument()
    expect(within(approved!).getByText('STABLE_INCOME_MANUAL_CHECK')).toBeInTheDocument()
  })

  it('opens the trace of a case in the order the engine walked it', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))

    const panel = within(await screen.findByRole('complementary'))
    // Document 3: the reason is the rule's own words, in the policy's language
    expect(panel.getByText(decision.reason!)).toBeInTheDocument()
    const steps = panel.getAllByRole('listitem')
    expect(steps).toHaveLength(decision.trace.length)
    expect(within(steps[0]!).getByText('R-170')).toBeInTheDocument()
    expect(within(steps[0]!).getByText('Did not match')).toBeInTheDocument()
    expect(within(steps[1]!).getByText('Matched')).toBeInTheDocument()
    expect(within(steps[2]!).getByText('Not reached')).toBeInTheDocument()
  })

  it('shows what each step compared and what the case carried', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))

    const panel = within(await screen.findByRole('complementary'))
    const first = panel.getAllByRole('listitem')[0]!
    // R-170 asks for an income below 8,000; the case carried 9,500, so the rule did not fire
    expect(within(first).getByText('monthly_income')).toBeInTheDocument()
    expect(within(first).getByText('< 8,000')).toBeInTheDocument()
    expect(within(first).getByText('9,500')).toBeInTheDocument()
    expect(within(first).getByText('not met')).toBeInTheDocument()
  })

  it('shows the values the engine derived and how long the case took', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))

    const panel = within(await screen.findByRole('complementary'))
    expect(panel.getByText('debt_to_income')).toBeInTheDocument()
    expect(panel.getByText('0.2835')).toBeInTheDocument()
    expect(panel.getByText('Decided in 0.4 ms by the engine')).toBeInTheDocument()
  })

  it('leads from the step that decided to the rule that decided it', async () => {
    const user = userEvent.setup()
    const opened = vi.fn()
    renderScreen(opened)

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))
    const panel = within(await screen.findByRole('complementary'))
    await user.click(panel.getByRole('button', { name: 'R-330' }))

    expect(opened).toHaveBeenCalledExactlyOnceWith('R-330')
  })

  it('shows the flags a rule raised on the case', async () => {
    const user = userEvent.setup()
    server.use(
      http.get(`${BASE}/decisions/:id`, () =>
        HttpResponse.json({
          ...decision,
          flags: [
            {
              code: 'STABLE_INCOME_MANUAL_CHECK',
              message: 'יציבות ההכנסה נבדקת ידנית',
              ruleId: 'R-420',
            },
          ],
        }),
      ),
    )
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))

    const panel = within(await screen.findByRole('complementary'))
    expect(panel.getByText('STABLE_INCOME_MANUAL_CHECK')).toBeInTheDocument()
    expect(panel.getByText('R-420')).toBeInTheDocument()
  })

  it('says that nothing was decided when the run is refused', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, () =>
        HttpResponse.json(
          { code: 'RATE_LIMITED', message: 'too many runs', traceId: '0f4c1c9e' },
          { status: 429 },
        ),
      ),
    )
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('RATE_LIMITED')
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('offers the run when the version has decided nothing yet', async () => {
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:no/stats`, () =>
        HttpResponse.json({ outcomes: {}, errors: 0, topDecidingRules: [], decisions: 0 }),
      ),
    )
    renderScreen()

    expect(await screen.findByText('Nothing decided yet')).toBeInTheDocument()
    // the action is offered where the eye already is, as well as in the header
    expect(screen.getAllByRole('button', { name: 'Run 200 cases' })).toHaveLength(2)
  })

  it('reports statistics that could not be read', async () => {
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:no/stats`, () =>
        HttpResponse.json(
          { code: 'INTERNAL_ERROR', message: 'no', traceId: '0f4c1c9e' },
          { status: 500 },
        ),
      ),
    )
    renderScreen()

    expect(await screen.findByText('INTERNAL_ERROR')).toBeInTheDocument()
  })
})
