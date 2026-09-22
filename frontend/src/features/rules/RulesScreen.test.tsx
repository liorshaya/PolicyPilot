import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { RuleSetDocument, RulesetsResponse, VersionResponse } from '../../api/types'
import { lendingRuleSet } from '../../test/fixtures/lending'
import {
  publishedVersion,
  rulesets,
  SEEDED_RULESET_ID,
  SECOND_RULESET_ID,
  twoRulesets,
} from '../../test/msw/handlers'
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

/** A draft whose review found nothing, so publishing waits for nothing (Document 2, Flow 1). */
const draftVersion: VersionResponse = {
  ...publishedVersion,
  protected: false,
  versionNo: 2,
  status: 'DRAFT',
  publishedAt: undefined,
  publishedBy: undefined,
  review: { status: 'DONE', promptVersion: 'v1', findings: [], coverage: {} },
}

/**
 * The review of the lending draft with three of the seeded findings of fixtures/eval/policies/consumer-lending/
 * seeded.findings.json: SF-1 the undefined "stable income" (paragraph 4, R-420), SF-2 the age conflict (paragraphs 1
 * and 8, R-110 and R-115), SF-4 the self-employed seniority clause without a rule (paragraph 3).
 */
const reviewedDraft: VersionResponse = {
  ...draftVersion,
  review: {
    status: 'DONE',
    promptVersion: 'v1',
    coverage: {},
    findings: [
      {
        id: 'F-1',
        kind: 'ambiguity',
        severity: 'warning',
        ruleIds: ['R-420'],
        paragraphIndexes: [4],
        message: 'הכנסה יציבה אינה מוגדרת',
        suggestion: 'להוסיף סימון לבדיקה ידנית',
        confidence: 0.8,
        blocking: false,
      },
      {
        id: 'F-2',
        kind: 'conflict',
        severity: 'error',
        ruleIds: ['R-110', 'R-115'],
        paragraphIndexes: [1, 8],
        message: 'סעיף 1 מגביל את הגיל ל-70 וסעיף 8 מתיר גמלאים עד 75',
        suggestion: 'להחריג גמלאים מ-R-110',
        confidence: 0.9,
        blocking: true,
      },
      {
        id: 'F-3',
        kind: 'gap',
        severity: 'warning',
        ruleIds: [],
        paragraphIndexes: [3],
        message: 'סעיף הוותק לעצמאים אינו מכוסה',
        suggestion: 'להוסיף כלל',
        confidence: 0.7,
        blocking: true,
      },
    ],
  },
}

function serveDraft(): void {
  server.use(
    http.get(`${BASE}/rulesets`, () => HttpResponse.json(draftRulesets)),
    http.get(`${BASE}/rulesets/:id/versions/:no`, () => HttpResponse.json(draftVersion)),
  )
}

function renderScreen(
  focusRuleId: string | null = null,
  rulesetId: string | null = null,
  onChooseRuleset: (id: string) => void = () => undefined,
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <RulesScreen
        onOpenCases={() => undefined}
        focusRuleId={focusRuleId}
        rulesetId={rulesetId}
        onChooseRuleset={onChooseRuleset}
      />
    </QueryClientProvider>,
  )
}

describe('RulesScreen', () => {
  it('shows the rules in evaluation order with the fields they compare', async () => {
    renderScreen()

    // one row per rule, under the band of Document 3 that its priority falls in
    const ruleRows = (await screen.findAllByRole('row')).filter(
      (row) => row.querySelector('.table__rule') !== null,
    )
    expect(ruleRows).toHaveLength(lendingRuleSet.rules.length)
    expect(within(ruleRows[0]!).getByText('R-010')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Derivations' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Positive outcome' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /debt_to_income/ })).toBeInTheDocument()
    // Document 3: the cell grammar, not a rendering of the JSON; a published version is read, not edited
    expect(within(ruleRows[2]!).getByText('< 21 years')).toBeInTheDocument()
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

  it('shows the rule set it was asked for, not the first one the API lists', async () => {
    // the sandbox holds the seeded lending rule set and a second one; day 8 found the screen pinned to the first
    server.use(...twoRulesets())
    renderScreen(null, SECOND_RULESET_ID)

    // a label of the deposit rule set, which the lending one does not contain
    expect(await screen.findByText('Deduct repairs and unpaid amounts')).toBeInTheDocument()
    expect(screen.queryByText(lendingRuleSet.rules[0]!.label)).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Rule set' })).toHaveValue(SECOND_RULESET_ID)
  })

  it('falls back to the first rule set when no one asked for a particular one', async () => {
    server.use(...twoRulesets())
    renderScreen(null, null)

    expect(await screen.findByText(lendingRuleSet.rules[0]!.label)).toBeInTheDocument()
    expect(screen.queryByText('Deduct repairs and unpaid amounts')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Rule set' })).toHaveValue(SEEDED_RULESET_ID)
  })

  it('offers the other rule sets of the sandbox and reports the choice', async () => {
    const chosen: string[] = []
    server.use(...twoRulesets())
    renderScreen(null, SEEDED_RULESET_ID, (id) => chosen.push(id))

    const switcher = await screen.findByRole('combobox', { name: 'Rule set' })
    await userEvent.selectOptions(switcher, SECOND_RULESET_ID)

    expect(chosen).toEqual([SECOND_RULESET_ID])
  })

  it('offers no choice when the sandbox holds one rule set', async () => {
    renderScreen()

    await screen.findByRole('table')
    expect(screen.queryByRole('combobox', { name: 'Rule set' })).not.toBeInTheDocument()
  })

  it('tells two rule sets of one policy apart in the switcher', async () => {
    // generating from a policy that already has a published rule set gives two with the same name
    server.use(
      // MSW takes the first match, so this list stands in front of the two-rule-set handlers
      http.get(`${BASE}/rulesets`, () =>
        HttpResponse.json({
          rulesets: [
            rulesets.rulesets[0]!,
            {
              ...rulesets.rulesets[0]!,
              id: SECOND_RULESET_ID,
              protected: false,
              domain: 'draft-of-it',
            },
          ],
        }),
      ),
      ...twoRulesets(),
    )
    renderScreen(null, SEEDED_RULESET_ID, () => undefined)

    const switcher = await screen.findByRole('combobox', { name: 'Rule set' })
    const labels = within(switcher)
      .getAllByRole('option')
      .map((option) => option.textContent)
    expect(new Set(labels).size).toBe(2)
    expect(labels[1]).toContain('draft-of-it')
  })
})

/**
 * The review on the rule set screen (Brief FR-5; Document 2, Flow 1 and the acknowledge route; Work Plan day 10: "the
 * findings rendering"). The draft is the lending draft with SF-1, SF-2 and SF-4 of the seeded findings.
 */
describe('RulesScreen, the review of a draft', () => {
  function serveReviewed(version: VersionResponse = reviewedDraft): void {
    server.use(
      http.get(`${BASE}/rulesets`, () => HttpResponse.json(draftRulesets)),
      http.get(`${BASE}/rulesets/:id/versions/:no`, () => HttpResponse.json(version)),
    )
  }

  function rowOf(ruleId: string): HTMLElement {
    const row = screen
      .getAllByRole('row')
      .find((one) => one.querySelector('.table__rule')?.textContent?.startsWith(ruleId))
    if (!row) {
      throw new Error(`no row for ${ruleId}`)
    }
    return row
  }

  it('marks the rows of the rules each finding names', async () => {
    serveReviewed()
    renderScreen()

    await screen.findByText('הכנסה יציבה אינה מוגדרת')

    expect(within(rowOf('R-110')).getByText('Conflict')).toBeInTheDocument()
    expect(within(rowOf('R-115')).getByText('Conflict')).toBeInTheDocument()
    expect(within(rowOf('R-420')).getByText('Ambiguity')).toBeInTheDocument()
    expect(within(rowOf('R-100')).queryByText('Conflict')).not.toBeInTheDocument()
  })

  it('says what publishing waits for and keeps the button disabled', async () => {
    serveReviewed()
    renderScreen()

    expect(
      await screen.findByText('Publishing waits: 2 findings must be acknowledged: F-2, F-3.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Publish version' })).toBeDisabled()
    expect(screen.getAllByText('Blocks publishing')).toHaveLength(2)
  })

  it('acknowledges a gap only with a resolution, and sends the one chosen', async () => {
    const user = userEvent.setup()
    serveReviewed()
    const sent: unknown[] = []
    server.use(
      http.post(
        `${BASE}/rulesets/:id/versions/:no/findings/:finding/acknowledge`,
        async ({ request, params }) => {
          sent.push({ finding: params.finding, body: await request.json() })
          const findings = reviewedDraft.review!.findings.map((one) =>
            one.id === 'F-3'
              ? {
                  ...one,
                  blocking: false,
                  acknowledgement: {
                    resolution: 'flag_added' as const,
                    at: '2026-09-24T09:00:00Z',
                  },
                }
              : one,
          )
          return HttpResponse.json({
            ...reviewedDraft,
            review: { ...reviewedDraft.review!, findings },
          })
        },
      ),
    )
    renderScreen()
    const gap = (await screen.findByText('סעיף הוותק לעצמאים אינו מכוסה')).closest('li')!

    await user.click(within(gap).getByRole('button', { name: 'Acknowledge' }))
    const record = within(gap).getByRole('button', { name: 'Record the acknowledgement' })
    expect(record).toBeDisabled()
    await user.click(within(gap).getByLabelText('A manual-check flag surfaces it'))
    await user.click(record)

    expect(
      await within(gap).findByText('Acknowledged: A manual-check flag surfaces it'),
    ).toBeInTheDocument()
    expect(sent).toEqual([{ finding: 'F-3', body: { resolution: 'flag_added' } }])
  })

  it('acknowledges an error only with a note, and sends the note', async () => {
    const user = userEvent.setup()
    serveReviewed()
    const sent: unknown[] = []
    server.use(
      http.post(
        `${BASE}/rulesets/:id/versions/:no/findings/:finding/acknowledge`,
        async ({ request }) => {
          sent.push(await request.json())
          return HttpResponse.json(reviewedDraft)
        },
      ),
    )
    renderScreen()
    const conflict = (
      await screen.findByText('סעיף 1 מגביל את הגיל ל-70 וסעיף 8 מתיר גמלאים עד 75')
    ).closest('li')!

    await user.click(within(conflict).getByRole('button', { name: 'Acknowledge' }))
    const record = within(conflict).getByRole('button', { name: 'Record the acknowledgement' })
    expect(record).toBeDisabled()
    await user.type(
      within(conflict).getByLabelText('Why the draft stands as it is (required)'),
      'R-110 is narrowed to non-retirees',
    )
    await user.click(record)

    await waitFor(() => expect(sent).toEqual([{ note: 'R-110 is narrowed to non-retirees' }]))
  })

  it('offers to review a draft that has no review yet, and shows what the review found', async () => {
    const user = userEvent.setup()
    serveReviewed({ ...draftVersion, review: undefined })
    let reviewed = 0
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/review`, () => {
        reviewed += 1
        return HttpResponse.json(reviewedDraft)
      }),
    )
    renderScreen()

    expect(
      await screen.findByText('Publishing waits: The draft has not been reviewed yet.'),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Review the draft' }))

    expect(await screen.findByText('הכנסה יציבה אינה מוגדרת')).toBeInTheDocument()
    expect(reviewed).toBe(1)
  })

  it('opens the paragraph a finding names beside the table', async () => {
    const user = userEvent.setup()
    serveReviewed()
    renderScreen()
    const ambiguity = (await screen.findByText('הכנסה יציבה אינה מוגדרת')).closest('li')!

    await user.click(within(ambiguity).getByRole('button', { name: 'Paragraph 4' }))

    expect(
      await screen.findByText('Paragraph 4, which a finding of the review names'),
    ).toBeInTheDocument()
  })

  it("writes the reviewer's Hebrew right to left", async () => {
    serveReviewed()
    renderScreen()

    expect(await screen.findByText('הכנסה יציבה אינה מוגדרת')).toHaveAttribute('dir', 'rtl')
  })
})
