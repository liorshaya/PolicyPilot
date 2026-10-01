import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import type { RuleSetDocument, RulesetsResponse, VersionResponse } from '../../api/types'
import { copyRuleset, copyVersion } from '../../test/fixtures/audit'
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
import { ENGLISH_RULESET_ID, englishRuleSet } from '../../test/fixtures/english'
import { rtlSnapshot } from '../../test/rtlSnapshot'

// @requirement NFR-5

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
  onOpenPolicies: () => void = () => undefined,
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <RulesScreen
        onOpenCases={() => undefined}
        onOpenPolicies={onOpenPolicies}
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
      (row) => row.querySelector('.t-rule') !== null,
    )
    expect(ruleRows).toHaveLength(lendingRuleSet.rules.length)
    expect(within(ruleRows[0]!).getByText('R-010')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Derivations 1–99' })).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: 'Positive outcome 900–999' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: 'debt_to_income, derived' }),
    ).toBeInTheDocument()
    // Document 3: the cell grammar, not a rendering of the JSON; a published version is read, not edited
    expect(within(ruleRows[2]!).getByText('21').closest('td')).toHaveTextContent(/^<21$/)
    expect(screen.queryByLabelText('R-100, age')).not.toBeInTheDocument()
  })

  it("names the rule count beside the section's title and offers the version, the tag and the margin's view", async () => {
    serveDraft()
    renderScreen()

    await screen.findByLabelText('R-100, age')
    // the spec, section 10: "Decision table · 20 rules", then Version, Tag and Policy · Rule · JSON
    expect(
      screen.getByRole('heading', { name: 'Decision table' }).closest('.sec'),
    ).toHaveTextContent('Decision table · 20 rules')
    expect(screen.getByRole('combobox', { name: 'Version' })).toHaveValue('2')
    const tag = screen.getByRole('combobox', { name: 'Tag' })
    expect(within(tag).getAllByRole('option')[0]).toHaveTextContent('All tags')
    const margin = screen.getByRole('group', { name: 'Show in the margin' })
    expect(margin).toHaveClass('segment')
    // the tabs stand at the margin's head, not in the sheet's title row (the spec, section 08, v3.6)
    expect(screen.getByRole('complementary')).toContainElement(margin)
    expect(
      screen.getByRole('heading', { name: 'Decision table' }).closest('.sec'),
    ).not.toContainElement(margin)
    // a draft's margin opens on the review of its rules (the owner's answer of 2026-09-28 to phase 3's fifth question)
    expect(within(margin).getByRole('button', { name: /^Review/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(within(margin).getByRole('button', { name: 'Rule' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
  })

  it('keeps the rules of the chosen tag, and says how many of them it shows', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.selectOptions(await screen.findByRole('combobox', { name: 'Tag' }), 'credit_history')

    // R-220 and R-330 carry credit_history (fixtures/policies/consumer-lending/ruleset.v1.json)
    expect(screen.getAllByRole('rowheader')).toHaveLength(2)
    expect(
      screen.getByRole('heading', { name: 'Decision table' }).closest('.sec'),
    ).toHaveTextContent('Decision table · 2 of 20 rules')
  })

  it('states the version, its status and that a seeded version is read-only', async () => {
    renderScreen()

    // the spec, section 10: the version's state beside the title, "Draft v2", "Published v1"; the glossary's words
    // for a seeded rule set, on the seeded status's well
    const title = await screen.findByRole('heading', { level: 1 })
    await waitFor(() =>
      expect(within(title).getByText('Published v1')).toHaveClass('vstatus', 'vstatus--published'),
    )
    expect(within(title).getByText('Seeded, read-only')).toHaveClass('vstatus', 'vstatus--seeded')
    // the spec, section 10: the primary names the version it would publish
    expect(screen.getByRole('button', { name: 'Publish version 1' })).toBeDisabled()
    expect(screen.getByText('Only a draft is published')).toHaveClass('reason')
  })

  it('opens the paragraph a rule cites when its row is selected', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: /R-330/ }))

    // R-330 is quoted from paragraph 7 of the Hebrew policy (fixtures/policies/consumer-lending)
    expect(await screen.findByText(/Paragraph 7 is the source of R-330/)).toBeInTheDocument()
    const cited = document.getElementById('paragraph-7')!
    expect(cited).toHaveAttribute('aria-current', 'true')
    expect(cited).toHaveTextContent('מבקש עם אירוע אחד יידרש להעמיד ערב')
    expect(cited.querySelector('.para__text')).toHaveAttribute('dir', 'rtl')
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
              fieldNames: [],
            },
          ],
        }),
      ),
    )
    renderScreen()

    await user.click(await screen.findByRole('button', { name: /R-330/ }))
    await user.click(screen.getByRole('button', { name: 'Rule' }))

    const panel = within(await screen.findByRole('complementary'))
    // the spec, section 10: the rule stacked label over value, its priority with its band
    expect(panel.getByText('Priority').nextElementSibling).toHaveTextContent(
      '330 · Referral conditions',
    )
    expect(panel.getByText('Manual review')).toHaveClass('tag', 'tag--refer')
    // the confidence the model gave the quotation (fixtures/policies/consumer-lending/ruleset.v1.json)
    expect(panel.getByText('model · confidence 0.88')).toHaveClass('actor', 'actor--model')
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
    await user.type(cell, '< 23{Enter}')

    await waitFor(() => expect(sent).not.toBeNull())
    const document = sent as unknown as RuleSetDocument
    expect(document.rules.find((rule) => rule.id === 'R-100')?.condition).toEqual({
      field: 'age',
      op: 'lt',
      value: 23,
    })
    expect(document.rules).toHaveLength(lendingRuleSet.rules.length)
  })

  it('switches a rule of a draft off in the margin and sends the whole document with it skipped', async () => {
    // Document 3, Rules: a rule whose enabled is false is skipped by the engine; Document 9 puts the control here
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

    await user.click(await screen.findByRole('button', { name: /R-310/ }))
    await user.click(screen.getByRole('checkbox', { name: 'Enabled' }))

    await waitFor(() => expect(sent).not.toBeNull())
    const document = sent as unknown as RuleSetDocument
    expect(document.rules.find((rule) => rule.id === 'R-310')?.enabled).toBe(false)
    expect(document.rules.filter((rule) => rule.enabled === false)).toHaveLength(1)
    expect(document.rules).toHaveLength(lendingRuleSet.rules.length)
  })

  // The spec (v3.8), section 09: a draft's Fields mark a case field required, the whole document sent as an edit is
  it('marks a case field of a draft required in its Fields and sends the whole document with it', async () => {
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

    const switcher = await screen.findByRole('group', { name: 'Show in the margin' })
    await user.click(within(switcher).getByRole('button', { name: 'Fields' }))
    const fields = await screen.findByRole('region', { name: 'Fields' })
    const row = [...fields.querySelectorAll<HTMLElement>('.schema__row')].find((one) =>
      one.textContent.startsWith('employment_months'),
    )!
    await user.click(within(row).getByRole('checkbox', { name: 'Required' }))

    await waitFor(() => expect(sent).not.toBeNull())
    const document = sent as unknown as RuleSetDocument
    expect(document.fields.find((field) => field.name === 'employment_months')?.required).toBe(true)
    expect(document.fields).toHaveLength(lendingRuleSet.fields.length)
    expect(document.rules).toStrictEqual(lendingRuleSet.rules)
  })

  // The strict check of 2026-10-01: each tick sent the document of its render, so a second tick before the first was
  // answered sent the draft without the first and the stored draft kept only the last. The boxes wait for the answer
  it('holds every Required box while an edit is saved, so a second tick builds on the first', async () => {
    const user = userEvent.setup()
    let answer: () => void = () => undefined
    const answered = new Promise<void>((resolve) => {
      answer = resolve
    })
    let sent: RuleSetDocument | null = null
    serveDraft()
    server.use(
      http.put(`${BASE}/rulesets/:id/versions/:no/rules`, async ({ request }) => {
        sent = (await request.json()) as RuleSetDocument
        await answered
        return HttpResponse.json({ ...draftVersion, ruleSet: sent })
      }),
    )
    renderScreen()

    const switcher = await screen.findByRole('group', { name: 'Show in the margin' })
    await user.click(within(switcher).getByRole('button', { name: 'Fields' }))
    const fields = await screen.findByRole('region', { name: 'Fields' })
    const box = () =>
      within(
        [...fields.querySelectorAll<HTMLElement>('.schema__row')].find((one) =>
          one.textContent.startsWith('employment_months'),
        )!,
      ).getByRole('checkbox', { name: 'Required' })
    await user.click(box())

    await waitFor(() => expect(sent).not.toBeNull())
    for (const each of within(fields).getAllByRole('checkbox', { name: 'Required' })) {
      expect(each).toBeDisabled()
    }
    answer()
    await waitFor(() => expect(box()).toBeEnabled())
    expect(box()).toBeChecked()
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
    await user.type(cell, '< 5{Enter}')

    const refusal = await screen.findByRole('alert')
    expect(refusal).toHaveTextContent('RULESET_INVALID')
    expect(within(refusal).getByText('/rules/2/condition/value')).toBeInTheDocument()
    expect(within(refusal).getByText('below the minimum of the field')).toBeInTheDocument()
    // and under the cell the pointer names, R-100's age (the spec, section 11: "422 on a cell")
    expect(
      screen
        .getByText('below the minimum of the field', { selector: '.t-cell-problem' })
        .closest('td'),
    ).toContainElement(screen.getByLabelText('R-100, age'))
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

    // the draft's review found nothing, so the margin shows the review and its publish box
    const margin = within(await screen.findByRole('complementary'))
    await user.click(await margin.findByRole('button', { name: 'Publish version 2' }))

    expect(
      await margin.findByText('Version 2 decides cases from now on. Version 1 stays readable.'),
    ).toBeInTheDocument()
    expect(within(screen.getByRole('heading', { level: 1 })).getByText('Published v2')).toHaveClass(
      'vstatus',
      'vstatus--published',
    )
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
              fieldNames: [],
            },
          ],
        }),
      ),
    )
    renderScreen()

    expect(
      await within(await screen.findByRole('heading', { level: 1 })).findByText('Draft v2'),
    ).toBeInTheDocument()
    // the header's primary and the publish box's button: one action, disabled in both places
    const publish = screen.getAllByRole('button', { name: 'Publish version 2' })
    expect(publish).toHaveLength(2)
    for (const button of publish) {
      expect(button).toBeDisabled()
    }
    expect(
      screen.getByText('Schema and semantics valid').closest('.publish-box__row'),
    ).toHaveTextContent('1 problem')
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

  // The owner's answer of 2026-09-28 to phase 5's second question: Policy · Rule · Fields · JSON, and a version with
  // no review opens on its fields until a rule is chosen, which shows the paragraph it cites
  it('offers Policy, Rule, Fields and JSON in the margin, and opens a version with no review on its fields', async () => {
    const user = userEvent.setup()
    renderScreen()

    const switcher = await screen.findByRole('group', { name: 'Show in the margin' })
    expect(
      within(switcher)
        .getAllByRole('button')
        .map((one) => one.textContent),
    ).toEqual(['Rule', 'Policy', 'Fields', 'JSON'])
    const fields = await screen.findByRole('region', { name: 'Fields' })
    expect(within(fields).getByText('9 case fields, 2 derived')).toBeInTheDocument()
    expect(within(switcher).getByRole('button', { name: 'Fields' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    await user.click(await screen.findByRole('button', { name: /R-330/ }))
    expect(await screen.findByText(/Paragraph 7 is the source of R-330/)).toBeInTheDocument()
    await user.click(within(switcher).getByRole('button', { name: 'Fields' }))
    expect(screen.getByRole('region', { name: 'Fields' })).toBeInTheDocument()
  })

  it("opens a field's paragraph from the Fields panel in the Policy view", async () => {
    const user = userEvent.setup()
    renderScreen()

    const fields = await screen.findByRole('region', { name: 'Fields' })
    await user.click(within(fields).getAllByRole('button', { name: 'Paragraph 4' })[0]!)

    expect(document.getElementById('paragraph-4')).toHaveAttribute('aria-current', 'true')
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

    await within(await screen.findByRole('heading', { level: 1 })).findByText('Draft v2')
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

  it('opens an earlier version of the rule set through the version picker', async () => {
    const asked: string[] = []
    server.use(
      http.get(`${BASE}/rulesets`, () => HttpResponse.json({ rulesets: [copyRuleset] })),
      http.get(`${BASE}/rulesets/:id/versions/:no`, ({ params }) => {
        asked.push(String(params.no))
        return HttpResponse.json(copyVersion(Number(params.no) === 1 ? 1 : 2))
      }),
    )
    renderScreen(null, copyRuleset.id)

    // the latest version first: version 2, where the approved change stands
    const title = await screen.findByRole('heading', { level: 1 })
    expect(await within(title).findByText('Published v2')).toBeVisible()
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Version' }), '1')

    expect(await within(title).findByText(/^(Published|Superseded) v1$/)).toBeVisible()
    expect(asked).toStrictEqual(['2', '1'])
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

/** The Rules row of the states matrix (the spec, section 11), one test per cell no other test covers. */
describe('RulesScreen, every state', () => {
  it('Rules · loading', async () => {
    let release: () => void = () => undefined
    const read = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:no`, async () => {
        await read
        return HttpResponse.json(publishedVersion)
      }),
    )
    renderScreen()

    const loading = await screen.findByText('Loading the rule set')
    expect(loading.closest('.loading')!.querySelectorAll('.loading__row')).toHaveLength(3)
    expect(screen.getByRole('heading', { level: 1, name: 'Rules' })).toBeInTheDocument()
    release()
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('Rules · empty', async () => {
    const onOpenPolicies = vi.fn()
    server.use(http.get(`${BASE}/rulesets`, () => HttpResponse.json({ rulesets: [] })))
    renderScreen(null, null, () => undefined, onOpenPolicies)

    const sentence = await screen.findByText('No rule set yet')
    expect(sentence).toHaveClass('empty__rule--text')
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    await userEvent.click(
      within(sentence.closest<HTMLElement>('.empty')!).getByRole('button', {
        name: 'Generate rules from the policy',
      }),
    )
    expect(onOpenPolicies).toHaveBeenCalledOnce()
  })

  // The spec, section 11: "VERSION_STATUS_CONFLICT: 'Only a draft is published'" (Document 2: 409 on a version that is
  // not a DRAFT or is protected)
  it('Rules · VERSION_STATUS_CONFLICT', async () => {
    const user = userEvent.setup()
    serveDraft()
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/publish`, () =>
        HttpResponse.json(
          { code: 'VERSION_STATUS_CONFLICT', message: 'not a draft', details: [], traceId: 't' },
          { status: 409 },
        ),
      ),
    )
    renderScreen()

    const margin = within(await screen.findByRole('complementary'))
    await user.click(await margin.findByRole('button', { name: 'Publish version 2' }))

    const refusal = await screen.findByRole('alert')
    expect(refusal.querySelector('.refusal__head')!.textContent).toBe(
      'VERSION_STATUS_CONFLICTThe version was not published.',
    )
    expect(refusal.querySelector('.refusal__foot')!.textContent).toBe(
      'Only a draft is published. Nothing was stored.',
    )
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
    const row = screen.getByText(ruleId, { selector: '.t-rule__id' }).closest('tr')
    if (!row) {
      throw new Error(`no row for ${ruleId}`)
    }
    return row
  }

  // the spec, section 08 (v3.6): the Rule tab with no row chosen asks for one, rather than show the policy
  it('asks for a row on the Rule tab while none is chosen, and offers the Review tab with its count on a draft', async () => {
    const user = userEvent.setup()
    serveReviewed()
    renderScreen()

    await screen.findByRole('region', { name: 'Review of the draft' })
    const tabs = screen.getByRole('group', { name: 'Show in the margin' })
    expect(
      within(tabs)
        .getAllByRole('button')
        .map((one) => one.textContent),
    ).toEqual(['Review3', 'Rule', 'Policy', 'Fields', 'JSON'])
    expect(
      within(tabs)
        .getByRole('button', { name: /^Review/ })
        .querySelector('.count'),
    ).toHaveTextContent('3')

    await user.click(within(tabs).getByRole('button', { name: 'Rule' }))

    const rule = await screen.findByRole('region', { name: 'Rule' })
    expect(rule).toHaveTextContent(
      'Choose a row of the table to see the rule, its source and its findings',
    )
    expect(screen.queryByRole('region', { name: 'Review of the draft' })).not.toBeInTheDocument()
  })

  it('marks the rows of the rules each finding names', async () => {
    serveReviewed()
    renderScreen()

    await screen.findByText('הכנסה יציבה אינה מוגדרת')

    // the spec, section 07: a mark in the gutter, its finding in the title ("F-1 Conflict")
    expect(within(rowOf('R-110')).getByTitle('F-2 Conflict')).toHaveClass('sev--error')
    expect(within(rowOf('R-115')).getByTitle('F-2 Conflict')).toHaveClass('sev--error')
    expect(within(rowOf('R-420')).getByTitle('F-1 Ambiguity')).toHaveClass('sev--warning')
    expect(within(rowOf('R-100')).queryByTitle('F-2 Conflict')).not.toBeInTheDocument()
  })

  it('counts above the table what blocks publishing and what warns', async () => {
    serveReviewed()
    renderScreen()

    await screen.findByText('הכנסה יציבה אינה מוגדרת')

    // F-2 and F-3 block publishing, F-1 warns
    expect(screen.getByText('2 block')).toHaveClass('sev--error')
    expect(screen.getByText('1 warn')).toHaveClass('sev--warning')
  })

  it('says what publishing waits for and keeps the button disabled', async () => {
    serveReviewed()
    renderScreen()

    // the ids are kept whole on their line, so the sentence is read from its row
    await waitFor(() =>
      expect(
        document.querySelector('.publish-box__row--action .publish-box__reason'),
      ).toHaveTextContent('Publishing waits: 2 findings must be acknowledged: F-2, F-3.'),
    )
    // the spec, section 10: the header's primary names the version, and says why it waits beside it
    expect(screen.getByText('2 findings to acknowledge')).toHaveClass('reason')
    for (const button of screen.getAllByRole('button', { name: 'Publish version 2' })) {
      expect(button).toBeDisabled()
    }
    expect(screen.getAllByText('Blocks publishing')).toHaveLength(2)
  })

  it('shows the review in the margin of a draft until a rule is chosen, and goes back to it', async () => {
    // the owner's answer of 2026-09-28 to phase 3's fifth question
    const user = userEvent.setup()
    serveReviewed()
    renderScreen()

    const margin = within(await screen.findByRole('complementary'))
    expect(await margin.findByRole('region', { name: 'Review of the draft' })).toBeInTheDocument()
    expect(margin.getByText('Publishing version 2')).toHaveClass('publish-box__row--head')
    expect(screen.getByRole('button', { name: /^Review/ })).toHaveAttribute('aria-pressed', 'true')

    await user.click(within(rowOf('R-110')).getByRole('button', { name: /R-110/ }))

    // the chosen rule's three sections: the rule, the paragraph it cites, the findings that name it
    expect(margin.queryByRole('region', { name: 'Review of the draft' })).not.toBeInTheDocument()
    expect(
      [...document.querySelectorAll('aside .margin__title')].map((title) => title.textContent),
    ).toStrictEqual([
      'Rule R-110Draft',
      'Policy ¶\u00a01Open the policy',
      'Findings on this rule1 of 3',
    ])

    expect(screen.getByRole('button', { name: 'Rule' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(margin.getByRole('button', { name: 'All 3, in the review' }))

    expect(margin.getByRole('region', { name: 'Review of the draft' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Review/ })).toHaveAttribute('aria-pressed', 'true')
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

    // the acknowledged finding is the inline seal, the resolution chosen under it
    expect(await within(gap).findByText('A manual-check flag surfaces it')).toBeInTheDocument()
    expect(gap.querySelector('.seal')).toHaveTextContent('Acknowledged09:00 · Analyst')
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

describe('RulesScreen, gone to from the palette', () => {
  function renderAt(focus: { focusFindingId?: string; focusVersionNo?: number }) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <RulesScreen
          onOpenCases={() => undefined}
          onOpenPolicies={() => undefined}
          rulesetId={SEEDED_RULESET_ID}
          {...focus}
        />
      </QueryClientProvider>,
    )
  }

  // the spec, section 08: F-1 opens "in the review"; the finding is the current one, brought into view
  it('opens on the finding the palette names: the review in the margin, that finding current and in view', async () => {
    server.use(
      http.get(`${BASE}/rulesets`, () => HttpResponse.json(draftRulesets)),
      http.get(`${BASE}/rulesets/:id/versions/:no`, () => HttpResponse.json(reviewedDraft)),
    )
    const scrolled = vi.spyOn(Element.prototype, 'scrollIntoView')
    renderAt({ focusFindingId: 'F-2' })

    const review = await screen.findByRole('region', { name: 'Review of the draft' })
    const current = await waitFor(() => {
      const found = review.querySelector('.finding[aria-current="true"]')
      expect(found).not.toBeNull()
      return found!
    })
    expect(current).toHaveTextContent('F-2')
    expect(current).toHaveClass('flash')
    expect(review.querySelectorAll('.finding[aria-current="true"]')).toHaveLength(1)
    await waitFor(() => expect(scrolled.mock.contexts).toContain(current))
    scrolled.mockRestore()
  })

  // the spec, section 02: a chip that opens a row brings it into view, and the row flashes once
  it("brings the row a finding's rule chip opens into view, and flashes it", async () => {
    server.use(
      http.get(`${BASE}/rulesets`, () => HttpResponse.json(draftRulesets)),
      http.get(`${BASE}/rulesets/:id/versions/:no`, () => HttpResponse.json(reviewedDraft)),
    )
    const scrolled = vi.spyOn(Element.prototype, 'scrollIntoView')
    renderAt({})
    const user = userEvent.setup()
    const review = await screen.findByRole('region', { name: 'Review of the draft' })
    const conflict = within(review).getByText('F-2').closest('li')!

    await user.click(within(conflict).getByRole('button', { name: 'R-115' }))

    const row = screen.getByText('R-115', { selector: '.t-rule__id' }).closest('tr')!
    expect(row).toHaveClass('t-selected', 'flash')
    await waitFor(() => expect(scrolled.mock.contexts).toContain(row))
    // a row chosen in the table itself is not a deep link: it neither flashes nor moves
    await user.click(screen.getByText('R-100', { selector: '.t-rule__id' }))
    expect(screen.getByText('R-100', { selector: '.t-rule__id' }).closest('tr')).not.toHaveClass(
      'flash',
    )
    scrolled.mockRestore()
  })

  it('opens on the version the palette names, not the latest', async () => {
    const asked: string[] = []
    server.use(
      http.get(`${BASE}/rulesets`, () => HttpResponse.json(draftRulesets)),
      http.get(`${BASE}/rulesets/:id/versions/:no`, ({ params }) => {
        asked.push(String(params.no))
        return HttpResponse.json(params.no === '1' ? publishedVersion : draftVersion)
      }),
    )
    renderAt({ focusVersionNo: 1 })

    await waitFor(() =>
      expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Published v1'),
    )
    expect(asked).not.toContain('2')
  })
})

describe('RulesScreen in both directions (NFR-5)', () => {
  it('RTL: Hebrew labels turn right to left beside Latin ids, fields and numbers (snapshot)', async () => {
    renderScreen()

    const table = await screen.findByRole('table')
    const label = await within(table).findByText(lendingRuleSet.rules[0]!.label)
    // the rule set's language is known, so its labels say it (the spec, section 03, the bidi law, clause 4)
    expect(label).toHaveAttribute('dir', 'rtl')
    expect(label).toHaveAttribute('lang', 'he')
    expect(rtlSnapshot(table)).toMatchSnapshot()
  })

  it('LTR: an English rule set stays left to right (snapshot)', async () => {
    server.use(
      http.get(`${BASE}/rulesets`, () =>
        HttpResponse.json({
          rulesets: [
            {
              id: ENGLISH_RULESET_ID,
              name: englishRuleSet.name,
              domain: englishRuleSet.id,
              protected: false,
              versions: [{ versionNo: 1, status: 'PUBLISHED' }],
            },
          ],
        }),
      ),
      http.get(`${BASE}/rulesets/:id/versions/:no`, () =>
        HttpResponse.json({
          ...publishedVersion,
          rulesetId: ENGLISH_RULESET_ID,
          name: englishRuleSet.name,
          domain: englishRuleSet.id,
          protected: false,
          ruleSet: englishRuleSet,
        }),
      ),
    )
    renderScreen()

    const table = await screen.findByRole('table')
    await within(table).findByText(englishRuleSet.rules[0]!.label)
    expect(rtlSnapshot(table)).toMatchSnapshot()
  })
})
