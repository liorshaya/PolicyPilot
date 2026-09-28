import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import {
  aggregates,
  batch,
  decision,
  publishedVersion,
  rulesets,
  SECOND_RULESET_ID,
  secondRuleset,
  SEEDED_RULESET_ID,
  twoRulesets,
} from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { CasesScreen } from './CasesScreen'
import { lendingCase17, lendingRuleSet } from '../../test/fixtures/lending'
import { ENGLISH_RULESET_ID, englishDecision, englishRuleSet } from '../../test/fixtures/english'
import { rtlSnapshot } from '../../test/rtlSnapshot'

// @requirement NFR-5

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

    // the spec, section 09: "113 56.5%" of the 200 seeded cases, once the statistics are read (a dash until then)
    await waitFor(() =>
      expect(screen.getByText('Approved').closest('.figure')).toHaveTextContent('Approved11356.5%'),
    )
    expect(screen.getByRole('heading', { level: 2, name: 'Decisions on v1' })).toBeInTheDocument()
    expect(
      screen.getByText('Rules that decided most often · click to filter the list'),
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
    // the spec, section 09: "Decisions on v1 · 200 cases · one run"; the served run holds two
    expect(await screen.findByText('2 cases · one run')).toBeInTheDocument()
  })

  it('counts a run of one case as one case', async () => {
    const user = userEvent.setup()
    const one = { ...batch, results: batch.results.slice(0, 1) }
    server.use(http.post(`${BASE}/rulesets/:id/versions/:no/decide`, () => HttpResponse.json(one)))
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))

    expect(await screen.findByText(/^1 case · one run$/)).toBeInTheDocument()
  })

  it('filters the case list by a rule the figures name', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await screen.findByRole('button', { name: '18' })
    await user.click(screen.getByRole('button', { name: /^R-330/ }))

    // case 17 was decided by R-330, case 18 by R-900
    expect(screen.getByRole('combobox', { name: 'Deciding rule' })).toHaveValue('R-330')
    expect(screen.getByRole('button', { name: '17' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '18' })).not.toBeInTheDocument()
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
    const steps = () => panel.getAllByRole('listitem')
    // R-900 was not reached after the decision: counted under the steps until every comparison is shown (the owner's
    // answer of 2026-09-28 to phase 3's sixth question)
    expect(steps()).toHaveLength(2)
    expect(within(steps()[0]!).getByText('R-170')).toBeInTheDocument()
    expect(within(steps()[0]!).getByText('Did not match')).toBeInTheDocument()
    expect(within(steps()[1]!).getByText('Matched · decided')).toBeInTheDocument()

    await user.click(panel.getByRole('button', { name: 'Show every comparison' }))

    expect(steps()).toHaveLength(decision.trace.length)
    expect(within(steps()[2]!).getByText('Not reached')).toBeInTheDocument()
  })

  it('shows what each step compared and what the case carried', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))

    const panel = within(await screen.findByRole('complementary'))
    await user.click(panel.getByRole('button', { name: 'Show every comparison' }))
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
    // the label of the deciding rule is Hebrew too, pinned above the steps and in its own step, beside Latin ids
    const panel = within(await screen.findByRole('complementary'))
    const labels = panel.getAllByText('בדיקת חתם: אירוע אשראי אחד ללא ערב')
    expect(labels).toHaveLength(2)
    for (const label of labels) {
      expect(label).toHaveAttribute('dir', 'rtl')
      expect(label).toHaveAttribute('lang', 'he')
    }
    expect(panel.getByText('credit_events_24m')).toHaveClass('cmp__field')
  })

  it('shows the values the engine derived and how long the case took', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))

    const panel = within(await screen.findByRole('complementary'))
    expect(panel.getByText('debt_to_income')).toBeInTheDocument()
    expect(panel.getByText('0.2835')).toBeInTheDocument()
    // the spec, section 04: the engine's mark with its time, and no version (phase 3's second question)
    expect(panel.getByText('engine · 412 µs')).toHaveClass('actor', 'actor--engine')
  })

  it('leads from the rule that decided the case to the rule on the Rules screen', async () => {
    const user = userEvent.setup()
    const opened = vi.fn()
    renderScreen(opened)

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))
    const panel = within(await screen.findByRole('complementary'))
    const deciding = panel.getByText('Decided by').closest<HTMLElement>('.trace__deciding')!
    await user.click(within(deciding).getByRole('button', { name: 'R-330' }))

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
    expect(panel.getByRole('button', { name: 'R-420' })).toHaveClass('chip', 'chip--id')
    // the message is the engine's, in the policy's language, hung under its code
    expect(panel.getByText('יציבות ההכנסה נבדקת ידנית')).toHaveAttribute('dir', 'rtl')
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

  // The spec, section 10, the Cases screen: the header's one secondary action is Decide a case, beside the primary
  it('offers Decide a case beside Run 200 cases, and opens its form in the margin', async () => {
    const user = userEvent.setup()
    renderScreen()

    await screen.findByText('Approved')
    const actions = document.querySelector<HTMLElement>('.ws-header__side')!
    expect(
      within(actions)
        .getAllByRole('button')
        .map((one) => one.textContent),
    ).toEqual(['Decide a case', 'Run 200 cases'])
    expect(within(actions).getByRole('button', { name: 'Decide a case' })).toHaveClass(
      'btn--secondary',
    )
    await user.click(within(actions).getByRole('button', { name: 'Decide a case' }))

    const margin = screen.getByRole('complementary', { name: 'Decide a case' })
    expect(margin.querySelector('.case-form')).not.toBeNull()
    expect(within(margin).getByText('Decided by', { exact: false })).toHaveTextContent(
      'Decided by v1, the published version, and recorded like any other decision.',
    )
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

    // the spec's note, in the system's voice (sections 08 and 11)
    const note = await screen.findByText(
      'This rule set has no published version yet; the cases ran on the seeded one.',
    )
    expect(note.closest('.note')!.querySelector('.actor--system')).not.toBeNull()
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

/** The Cases row of the states matrix (the spec, section 11), one test per cell no other test covers. */
describe('CasesScreen, gone to from the palette', () => {
  // the spec, section 08: the palette reaches a case of this session's run by its number (the owner's answer to phase
  // 6's second question), and opening it shows its trace, the row kept in view
  it("keeps this session's run when it opens again, and opens the case the palette names in the margin", async () => {
    const user = userEvent.setup()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const first = render(
      <QueryClientProvider client={client}>
        <CasesScreen onOpenRule={() => undefined} />
      </QueryClientProvider>,
    )
    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await screen.findByRole('button', { name: '18' })
    first.unmount()
    const scrolled = vi.spyOn(Element.prototype, 'scrollIntoView')

    render(
      <QueryClientProvider client={client}>
        <CasesScreen onOpenRule={() => undefined} focusDecisionId={decision.id} />
      </QueryClientProvider>,
    )

    // the run's cases are listed again without running them again, case 17 the selected row
    const row = (await screen.findByRole('button', { name: '17' })).closest('tr')!
    expect(screen.getByRole('button', { name: '18' })).toBeInTheDocument()
    expect(row).toHaveAttribute('aria-current', 'true')
    expect(await screen.findByRole('complementary', { name: 'Case 17' })).toBeInTheDocument()
    await waitFor(() => expect(scrolled.mock.contexts).toContain(row))
    scrolled.mockRestore()
  })
})

describe('CasesScreen, every state', () => {
  it('Cases · loading', async () => {
    let release: () => void = () => undefined
    const read = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:no/stats`, async () => {
        await read
        return HttpResponse.json(aggregates)
      }),
    )
    renderScreen()

    // still rows, and the three figures under their words with a dash for the number not read yet
    const loading = await screen.findByText('Loading the statistics')
    expect(loading.closest('.loading')!.querySelectorAll('.loading__row')).toHaveLength(3)
    const figures = [...document.querySelectorAll<HTMLElement>('.figure')]
    expect(figures.map((one) => one.querySelector('.figure__label')!.textContent)).toEqual([
      'Approved',
      'Manual review',
      'Declined',
    ])
    expect(figures.map((one) => one.querySelector('.figure__value')!.textContent)).toEqual([
      '—',
      '—',
      '—',
    ])
    release()
    await waitFor(() =>
      expect(document.querySelector('.figure')).toHaveTextContent('Approved11356.5%'),
    )
  })

  it('Cases · empty', async () => {
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:no/stats`, () =>
        HttpResponse.json({ outcomes: {}, errors: 0, topDecidingRules: [], decisions: 0 }),
      ),
    )
    renderScreen()

    // "Nothing decided yet." on the ruled lines, and the run offered where the eye already is (section 08's empty)
    const sentence = await screen.findByText('Nothing decided yet.')
    expect(sentence).toHaveClass('empty__rule--text')
    const empty = sentence.closest<HTMLElement>('.empty')!
    const run = within(empty).getByRole('button', { name: 'Run 200 cases' })
    // one primary per screen, the header's: the empty sheet's action is a small secondary
    expect(run).toHaveClass('btn', 'btn--secondary', 'btn--sm')
    expect(within(empty).getByText('the seeded set, on version 1')).toHaveClass('muted')
  })

  // The owner's answer of 2026-09-28 to phase 5's fourth question: the row in ink, the code in the trace it opens
  it('Cases · evaluation error', async () => {
    const user = userEvent.setup()
    const failedId = '0f4c1c9e-0000-4000-8000-0000000000e3'
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, () =>
        HttpResponse.json({
          ...batch,
          results: [...batch.results, { id: failedId, caseNo: 19, status: 'ERROR', flags: [] }],
        }),
      ),
      http.get(`${BASE}/decisions/${failedId}`, () =>
        HttpResponse.json({
          ...decision,
          id: failedId,
          caseNo: 19,
          status: 'ERROR',
          outcome: undefined,
          reason: undefined,
          decidingRuleId: null,
          errorCode: 'DIVISION_BY_ZERO',
          errorRuleId: 'R-020',
          trace: [
            {
              ruleId: 'R-020',
              label: 'יחס החוב להכנסה',
              priority: 20,
              status: 'error',
              error: { code: 'DIVISION_BY_ZERO', detail: 'monthly_income is 0' },
            },
          ],
        }),
      ),
    )
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    const row = (await screen.findByRole('button', { name: '19' })).closest('tr')!
    // the ink tag and its words, never a colour (the spec, section 06)
    expect(within(row).getByText('Evaluation error')).toHaveClass('tag', 'tag--error')
    expect(row).not.toHaveTextContent('DIVISION_BY_ZERO')
    await user.click(within(row).getByRole('button', { name: '19' }))

    const trace = await screen.findByRole('complementary')
    expect(await within(trace).findByText('DIVISION_BY_ZERO · R-020')).toBeInTheDocument()
  })

  // The spec, section 11: "Decide a case → its trace"; Document 2: one case returns the full decision with its trace
  it('Cases · Decide a case, its trace', async () => {
    const user = userEvent.setup()
    let sent: unknown = null
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json(decision)
      }),
    )
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Decide a case' }))
    const form = screen.getByRole('complementary', { name: 'Decide a case' })
    for (const [name, value] of Object.entries(lendingCase17)) {
      const description = lendingRuleSet.fields.find((one) => one.name === name)!.description!
      const input = within(form).getByLabelText(description, { exact: false })
      if (input instanceof HTMLSelectElement) {
        await user.selectOptions(input, String(value))
      } else {
        await user.type(input, String(value))
      }
    }
    await user.click(within(form).getByRole('button', { name: 'Decide' }))

    // the margin turns to the decision's trace, the form gone
    const trace = await screen.findByRole('complementary', { name: 'Case 17' })
    expect(sent).toEqual({ case: lendingCase17 })
    expect(within(trace).getByText(decision.reason!)).toBeInTheDocument()
    expect(document.querySelector('.case-form')).toBeNull()
  })

  // The spec, section 11: "a case the schema refuses is refused whole (CASE_INVALID), the field named under its
  // input, nothing stored"
  it('Cases · CASE_INVALID', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, () =>
        HttpResponse.json(
          {
            code: 'CASE_INVALID',
            message: "The case is not valid against the rule set's fields.",
            details: [{ path: '/case/age', problem: 'CASE_REQUIRED_MISSING' }],
            traceId: 't',
          },
          { status: 422 },
        ),
      ),
    )
    renderScreen()

    await user.click(await screen.findByRole('button', { name: 'Decide a case' }))
    const form = screen.getByRole('complementary', { name: 'Decide a case' })
    await user.click(within(form).getByRole('button', { name: 'Decide' }))

    const age = within(form).getByLabelText('גיל המבקש בעת הגשת הבקשה', { exact: false })
    await waitFor(() => expect(age).toHaveAttribute('aria-invalid', 'true'))
    expect(document.getElementById(`${age.id}-error`)).toHaveTextContent(
      'This field is required. CASE_REQUIRED_MISSING',
    )
    expect(within(form).getByText('Nothing was stored.')).toBeInTheDocument()
    // the form stays, and no trace opens
    expect(screen.getByRole('complementary', { name: 'Decide a case' })).toBeInTheDocument()
  })
})

describe('CasesScreen in both directions (NFR-5)', () => {
  async function openCase17() {
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Run 200 cases' }))
    await user.click(await screen.findByRole('button', { name: '17' }))
    return screen.findByRole('complementary')
  }

  it('RTL: the trace of case 17 reads its Hebrew reasons right to left beside Latin fields and values (snapshot)', async () => {
    renderScreen()

    const trace = await openCase17()
    expect(await within(trace).findByText(decision.reason!)).toHaveAttribute('dir', 'rtl')
    expect(rtlSnapshot(trace)).toMatchSnapshot()
  })

  it('LTR: an English trace stays left to right (snapshot)', async () => {
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
      http.get(`${BASE}/decisions/:id`, () => HttpResponse.json(englishDecision)),
    )
    renderScreen()

    const trace = await openCase17()
    expect(await within(trace).findByText(englishDecision.reason!)).toHaveAttribute('dir', 'ltr')
    expect(rtlSnapshot(trace)).toMatchSnapshot()
  })
})
