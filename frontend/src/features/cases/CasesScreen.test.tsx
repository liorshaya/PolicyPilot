import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import {
  aggregates,
  batch,
  decision,
  rulesets,
  SECOND_RULESET_ID,
  secondRuleset,
  SEEDED_RULESET_ID,
  twoRulesets,
} from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { CasesScreen } from './CasesScreen'

/**
 * The case runner against realistic responses (Document 6, Frontend Test Design). The aggregates are those of the
 * 200 seeded cases, and the trace is the one the engine writes for a referred case.
 */

const BASE = 'http://localhost:8080/api/v1'

function renderScreen(
  onOpenRule: (ruleId: string | null) => void = () => undefined,
  rulesetId: string | null = null,
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <CasesScreen onOpenRule={onOpenRule} rulesetId={rulesetId} />
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

  it('reads a Hebrew reason right to left, beside Latin field names that stay as they are', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))

    const reason = await screen.findByText(decision.reason!)
    expect(reason).toHaveAttribute('dir', 'rtl')
    expect(reason).toHaveAttribute('lang', 'he')
    // the label of a step is Hebrew too, and keeps its own order beside the rule id
    const panel = within(await screen.findByRole('complementary'))
    const label = panel.getByText('בדיקת חתם: אירוע אשראי אחד ללא ערב')
    expect(label.tagName).toBe('BDI')
    expect(label).toHaveAttribute('dir', 'auto')
    expect(panel.getByText('monthly_income')).toHaveClass('mono')
  })

  it('shows the values the engine derived and how long the case took', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))

    const panel = within(await screen.findByRole('complementary'))
    expect(panel.getByText('debt_to_income')).toBeInTheDocument()
    expect(panel.getByText('0.2835')).toBeInTheDocument()
    expect(panel.getByText('Decided in 412 µs by the engine')).toBeInTheDocument()
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

  it('closes the trace and leaves the run on the screen', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))
    expect(await screen.findByRole('complementary')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Close' }))

    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
  })

  it('leads to the rules of the version it is running', async () => {
    const user = userEvent.setup()
    const opened = vi.fn()
    renderScreen(opened)

    await user.click(await screen.findByRole('button', { name: 'Open the rules' }))

    expect(opened).toHaveBeenCalledExactlyOnceWith(null)
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

  it('runs the rule set it was asked for, not the first one the API lists', async () => {
    let asked = ''
    server.use(
      // the second rule set is published here: only a published version decides (Document 2, decide); MSW takes
      // the first matching handler of one use(), so this one goes before the two rule sets' own list
      http.get(`${BASE}/rulesets`, () =>
        HttpResponse.json({
          rulesets: [
            rulesets.rulesets[0]!,
            { ...secondRuleset, versions: [{ versionNo: 1, status: 'PUBLISHED' }] },
          ],
        }),
      ),
      ...twoRulesets(),
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, ({ params }) => {
        asked = String(params.id)
        return HttpResponse.json(batch)
      }),
    )
    renderScreen(() => undefined, SECOND_RULESET_ID)

    await userEvent.click(await screen.findByRole('button', { name: 'Run 200 cases' }))

    await waitFor(() => expect(asked).toBe(SECOND_RULESET_ID))
  })

  // Document 2, decide: "Only a PUBLISHED version decides (409 otherwise)". The workspace is on a draft written from
  // the policy a moment ago (demo step 1), so the cases run on the seeded version, which is published, and say so
  it('runs the cases on the published seeded set when the workspace is on a draft never published', async () => {
    const user = userEvent.setup()
    const DRAFT_ID = '0f4c1c9e-0000-4000-8000-0000000000b9'
    const decided: string[] = []
    server.use(
      http.get(`${BASE}/rulesets`, () =>
        HttpResponse.json({
          rulesets: [
            ...rulesets.rulesets,
            {
              ...rulesets.rulesets[0]!,
              id: DRAFT_ID,
              protected: false,
              versions: [{ versionNo: 1, status: 'DRAFT' }],
            },
          ],
        }),
      ),
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, ({ params }) => {
        decided.push(`${String(params.id)}/${String(params.no)}`)
        return HttpResponse.json(batch)
      }),
    )
    renderScreen(() => undefined, DRAFT_ID)

    expect(
      await screen.findByText(
        'The rule set on the workspace has no published version yet; the cases run on the seeded one.',
      ),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Run 200 cases' }))

    await waitFor(() => expect(decided).toEqual([`${SEEDED_RULESET_ID}/1`]))
  })

  // Document 2, decide: a rule set whose version 2 is still a draft decides on its published version 1
  it('runs a rule set with a newer draft on its published version', async () => {
    const user = userEvent.setup()
    const decided: string[] = []
    server.use(
      http.get(`${BASE}/rulesets`, () =>
        HttpResponse.json({
          rulesets: [
            {
              ...rulesets.rulesets[0]!,
              versions: [
                { versionNo: 1, status: 'PUBLISHED' },
                { versionNo: 2, status: 'DRAFT' },
              ],
            },
          ],
        }),
      ),
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, ({ params }) => {
        decided.push(`${String(params.id)}/${String(params.no)}`)
        return HttpResponse.json(batch)
      }),
    )
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))

    await waitFor(() => expect(decided).toEqual([`${SEEDED_RULESET_ID}/1`]))
    expect(screen.queryByText(/has no published version yet/)).not.toBeInTheDocument()
  })
})
