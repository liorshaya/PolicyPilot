import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { RuleSetDocument, RulesetsResponse, VersionResponse } from '../../api/types'
import { lendingRuleSet } from '../../test/fixtures/lending'
import { publishedVersion, rulesets, SEEDED_RULESET_ID } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { RulesScreen } from './RulesScreen'

/**
 * The rule set screen (Document 6, Frontend Test Design: the decision table, the 422 pointers and the Hebrew
 * direction). The rule set is the committed lending fixture, so every rule id, label and quote on the screen is
 * the demo's own.
 */

const BASE = 'http://localhost:8080/api/v1'

const draftRulesets: RulesetsResponse = {
  rulesets: [
    {
      ...rulesets.rulesets[0]!,
      protected: false,
      versions: [
        { versionNo: 1, status: 'PUBLISHED' },
        { versionNo: 2, status: 'DRAFT' },
      ],
    },
  ],
}

const draftVersion: VersionResponse = {
  ...publishedVersion,
  protected: false,
  versionNo: 2,
  status: 'DRAFT',
  publishedAt: undefined,
  publishedBy: undefined,
}

function serveDraft(): void {
  server.use(
    http.get(`${BASE}/rulesets`, () => HttpResponse.json(draftRulesets)),
    http.get(`${BASE}/rulesets/:id/versions/:no`, () => HttpResponse.json(draftVersion)),
  )
}

function renderScreen(focusRuleId: string | null = null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <RulesScreen onOpenCases={() => undefined} focusRuleId={focusRuleId} />
    </QueryClientProvider>,
  )
}

describe('RulesScreen', () => {
  it('shows the rules in evaluation order with the fields they compare', async () => {
    renderScreen()

    const rows = await screen.findAllByRole('row')
    // the header row, then the twenty rules of the committed rule set in priority order
    expect(rows).toHaveLength(lendingRuleSet.rules.length + 1)
    expect(within(rows[1]!).getByText('R-010')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /debt_to_income/ })).toBeInTheDocument()
    // Document 3: the cell grammar, not a rendering of the JSON; a published version is read, not edited
    expect(within(rows[3]!).getByText('< 21 years')).toBeInTheDocument()
    expect(screen.queryByLabelText('R-100, age')).not.toBeInTheDocument()
  })

  it('states the version, its status and that a seeded version is read-only', async () => {
    renderScreen()

    expect(await screen.findByText('Version 1')).toBeInTheDocument()
    expect(screen.getByText('Published')).toBeInTheDocument()
    expect(screen.getByText('Seeded, read-only')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Publish version' })).toBeDisabled()
  })

  it('opens the paragraph a rule cites when its row is selected', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: /R-330/ }))

    // R-330 is quoted from paragraph 7 of the Hebrew policy (fixtures/policies/consumer-lending)
    expect(await screen.findByText(/Paragraph 7 is the source of R-330/)).toBeInTheDocument()
    const quote = screen.getAllByText('מבקש עם אירוע אחד יידרש להעמיד ערב')[0]!
    expect(quote.closest('[dir]')).toHaveAttribute('dir', 'rtl')
  })

  it('shows one rule in full, with its source and its findings, in the rule panel', async () => {
    const user = userEvent.setup()
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:no`, () =>
        HttpResponse.json({
          ...publishedVersion,
          findings: [
            {
              code: 'DSL-311',
              severity: 'warning',
              path: '/rules/12',
              message: 'the rule is unreachable',
              ruleIds: ['R-330'],
            },
          ],
        }),
      ),
    )
    renderScreen()

    await user.click(await screen.findByRole('button', { name: /R-330/ }))
    await user.click(screen.getByRole('button', { name: 'Rule' }))

    const panel = within(await screen.findByRole('complementary'))
    expect(panel.getByText('330')).toBeInTheDocument()
    expect(panel.getByText('Manual review')).toBeInTheDocument()
    // the confidence the model gave the quotation (fixtures/policies/consumer-lending/ruleset.v1.json)
    expect(panel.getByText('0.88')).toBeInTheDocument()
    expect(panel.getByText('DSL-311')).toBeInTheDocument()
    expect(panel.getByText('the rule is unreachable')).toBeInTheDocument()
  })

  it('opens the rule another screen asked for, until another rule is chosen', async () => {
    const user = userEvent.setup()
    renderScreen('R-330')

    // the step that decided a case leads here, so that rule is the one already open
    expect(await screen.findByText(/Paragraph 7 is the source of R-330/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /R-100/ }))

    expect(await screen.findByText(/Paragraph 1 is the source of R-100/)).toBeInTheDocument()
  })

  it('shows the document the engine runs in the JSON view', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'JSON' }))

    expect(screen.getByText(/"dslVersion": "1.0"/)).toBeInTheDocument()
  })

  it('edits one cell of a draft and sends the whole document with that comparison changed', async () => {
    const user = userEvent.setup()
    let sent: RuleSetDocument | null = null
    serveDraft()
    server.use(
      http.put(`${BASE}/rulesets/:id/versions/:no/rules`, async ({ request }) => {
        sent = (await request.json()) as RuleSetDocument
        return HttpResponse.json({ ...draftVersion, ruleSet: sent })
      }),
    )
    renderScreen()

    const cell = await screen.findByLabelText('R-100, age')
    await user.clear(cell)
    await user.type(cell, '< 23 years{Enter}')

    await waitFor(() => expect(sent).not.toBeNull())
    const document = sent as unknown as RuleSetDocument
    expect(document.rules.find((rule) => rule.id === 'R-100')?.condition).toEqual({
      field: 'age',
      op: 'lt',
      value: 23,
    })
    expect(document.rules).toHaveLength(lendingRuleSet.rules.length)
  })

  it('keeps a cell that is not a comparison in the editor and says what a cell may hold', async () => {
    const user = userEvent.setup()
    let calls = 0
    serveDraft()
    server.use(
      http.put(`${BASE}/rulesets/:id/versions/:no/rules`, () => {
        calls += 1
        return HttpResponse.json(draftVersion)
      }),
    )
    renderScreen()

    const cell = await screen.findByLabelText('R-100, age')
    await user.clear(cell)
    await user.type(cell, 'under 21{Enter}')

    expect(await screen.findByText(/Write =/)).toBeInTheDocument()
    expect(cell).toHaveAttribute('aria-invalid', 'true')
    expect(calls).toBe(0)
  })

  it('shows the code and the pointers of a refused change', async () => {
    const user = userEvent.setup()
    serveDraft()
    server.use(
      http.put(`${BASE}/rulesets/:id/versions/:no/rules`, () =>
        HttpResponse.json(
          {
            code: 'RULESET_INVALID',
            message: 'the rule set was refused',
            details: [
              { path: '/rules/2/condition/value', problem: 'below the minimum of the field' },
            ],
            traceId: '0f4c1c9e',
          },
          { status: 422 },
        ),
      ),
    )
    renderScreen()

    const cell = await screen.findByLabelText('R-100, age')
    await user.clear(cell)
    await user.type(cell, '< 5 years{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent('RULESET_INVALID')
    expect(screen.getByText('/rules/2/condition/value')).toBeInTheDocument()
    expect(screen.getByText('below the minimum of the field')).toBeInTheDocument()
  })

  it('publishes a draft and shows the version it returned', async () => {
    const user = userEvent.setup()
    serveDraft()
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/publish`, () =>
        HttpResponse.json({
          ...draftVersion,
          status: 'PUBLISHED',
          publishedAt: '2026-09-20T10:00:00Z',
          publishedBy: 'demo-analyst',
        }),
      ),
    )
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Publish version' }))

    expect(await screen.findByText('Published')).toBeInTheDocument()
    expect(screen.queryByLabelText('R-100, age')).not.toBeInTheDocument()
  })

  it('refuses to publish a draft that still has an error finding', async () => {
    serveDraft()
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:no`, () =>
        HttpResponse.json({
          ...draftVersion,
          findings: [
            {
              code: 'DSL-201',
              severity: 'error',
              path: '/rules/3/condition/field',
              message: 'unknown field',
              ruleIds: ['R-120'],
            },
          ],
        }),
      ),
    )
    renderScreen()

    expect(await screen.findByText('Draft')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Publish version' })).toBeDisabled()
  })

  it('reports a rule set that could not be read', async () => {
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:no`, () =>
        HttpResponse.json(
          { code: 'NOT_FOUND', message: 'no such version', traceId: '0f4c1c9e' },
          { status: 404 },
        ),
      ),
    )
    renderScreen()

    expect(await screen.findByText('NOT_FOUND')).toBeInTheDocument()
  })

  it('says so when there is no rule set at all', async () => {
    server.use(http.get(`${BASE}/rulesets`, () => HttpResponse.json({ rulesets: [] })))
    renderScreen()

    expect(await screen.findByText('No rule set yet')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('asks for the version the rule set list names last', async () => {
    let asked = ''
    serveDraft()
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:no`, ({ params }) => {
        asked = `${String(params.id)}/${String(params.no)}`
        return HttpResponse.json(draftVersion)
      }),
    )
    renderScreen()

    await screen.findByText('Version 2')
    expect(asked).toBe(`${SEEDED_RULESET_ID}/2`)
  })
})
