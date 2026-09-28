import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { RulesetsResponse, VersionResponse } from '../../api/types'
import { server } from '../../test/msw/server'
import { specRules, stylesheet, unported } from '../../test/css'
import { lendingParagraphs, lendingRuleSet } from '../../test/fixtures/lending'
import { seededReview } from '../../test/fixtures/review'
import {
  SEEDED_RULESET_ID,
  SECOND_POLICY_ID,
  SECOND_RULESET_ID,
  policies,
  publishedVersion,
  rulesets,
  secondPolicy,
  seededPolicy,
  secondVersion,
  twoRulesets,
} from '../../test/msw/handlers'
import { PoliciesScreen } from './PoliciesScreen'
import { englishParagraphs, englishPolicy } from '../../test/fixtures/english'
import { rtlSnapshot } from '../../test/rtlSnapshot'

// @requirement NFR-5

/**
 * The policy screen against realistic responses (Document 6, Frontend Test Design), as the Register composes it (the
 * spec, section 10, "Policies · author"; section 11, the Policies row). The policy is the committed Hebrew fixture, so
 * the direction, the paragraph numbers, the text and the rules that cite it are the demo's own (NFR-5).
 */

const BASE = 'http://localhost:8080/api/v1'

function renderScreen(onOpenRules: (rulesetId: string, ruleId?: string) => void = () => undefined) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <PoliciesScreen onOpenRules={onOpenRules} />
    </QueryClientProvider>,
  )
}

/** A generation stream the test writes event by event, so it can move the browser's clock between two of them. */
function streamed(
  write: (send: (name: string, data: unknown) => void, close: () => void) => void,
): HttpResponse<ReadableStream> {
  const stream = new ReadableStream({
    start(controller) {
      write(
        (name, data) =>
          controller.enqueue(
            new TextEncoder().encode(`event:${name}\ndata:${JSON.stringify(data)}\n\n`),
          ),
        () => controller.close(),
      )
    },
  })
  return new HttpResponse(stream, { headers: { 'Content-Type': 'text/event-stream' } })
}

function streamOf(events: [string, unknown][]): HttpResponse<string> {
  const body = events
    .map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`)
    .join('')
  return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream' } })
}

/** The lending draft a generation writes: version 2 of the lending rule set with the review of the seeded findings. */
const reviewedDraft: VersionResponse = {
  ...publishedVersion,
  protected: false,
  versionId: '0f4c1c9e-0000-4000-8000-0000000000c9',
  versionNo: 2,
  status: 'DRAFT',
  publishedAt: undefined,
  publishedBy: undefined,
  review: seededReview,
}

/** The seeded rule set with the reviewed draft as its latest version, so the sheet reads the draft's citations. */
const withReviewedDraft: RulesetsResponse = {
  rulesets: [
    {
      ...rulesets.rulesets[0]!,
      versions: [
        { versionNo: 1, status: 'PUBLISHED' },
        { versionNo: 2, status: 'DRAFT' },
      ],
    },
  ],
}

function serveReviewedDraft(): void {
  server.use(
    http.get(`${BASE}/rulesets`, () => HttpResponse.json(withReviewedDraft)),
    http.get(`${BASE}/rulesets/:id/versions/:no`, ({ params }) =>
      HttpResponse.json(params.no === '2' ? reviewedDraft : publishedVersion),
    ),
  )
}

/** The paragraph block of one paragraph of the lending policy, found by its text. */
async function paragraph(index: number): Promise<HTMLElement> {
  const text = await screen.findByText(lendingParagraphs[index - 1]!.text)
  return text.closest<HTMLElement>('.para')!
}

/** The text of every element of a kind inside one element, in order. */
function textsOf(element: HTMLElement, selector: string): string[] {
  return [...element.querySelectorAll(selector)].map((one) => one.textContent)
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('PoliciesScreen', () => {
  it('opens the seeded policy and numbers every paragraph', async () => {
    renderScreen()

    expect(await screen.findByText(lendingParagraphs[0]!.text)).toBeInTheDocument()
    expect(
      screen.getByText(lendingParagraphs[lendingParagraphs.length - 1]!.text),
    ).toBeInTheDocument()
    // the number beside a paragraph is what a rule cites (Document 3, Provenance)
    expect(textsOf(await paragraph(9), '.para__n')).toEqual(['9'])
  })

  it('reads a Hebrew policy right to left', async () => {
    renderScreen()

    const first = await screen.findByText(lendingParagraphs[0]!.text)
    const block = first.closest('[dir]')
    expect(block).toHaveAttribute('dir', 'rtl')
    expect(block).toHaveAttribute('lang', 'he')
  })

  // The spec, section 10: "The policy is the sheet: the serif at 17px, paragraph numbers in the gutter"
  it('sets the policy on the sheet in the serif at 17px, each paragraph numbered in the gutter', async () => {
    renderScreen()

    const first = await paragraph(1)
    expect(first).toHaveClass('para', 'para--sheet')
    // the document serif at --text-doc (17px); the margin's smaller size, doc--sm, is not the sheet's
    const text = first.querySelector('.para__text')!
    expect(text).toHaveClass('doc')
    expect(text).not.toHaveClass('doc--sm')
    // the gutter is the paragraph's first column, before its text
    expect(first.firstElementChild).toHaveClass('para__n')
    expect(first.firstElementChild).toHaveTextContent(/^1$/)
  })

  // The spec, section 10: "under each paragraph the rules that cite it". Expected: the quoted provenance of
  // fixtures/policies/consumer-lending/ruleset.v1.json, which the spec's composed screen draws the same
  it('lists under each paragraph the rules that cite it', async () => {
    renderScreen()

    expect(textsOf(await paragraph(1), '.para__cites .chip')).toEqual(['R-100', 'R-110'])
    expect(textsOf(await paragraph(3), '.para__cites .chip')).toEqual(['R-140', 'R-150', 'R-160'])
    expect(textsOf(await paragraph(5), '.para__cites .chip')).toEqual(['R-010'])
    expect(textsOf(await paragraph(6), '.para__cites .chip')).toEqual(['R-020', 'R-200', 'R-320'])
    expect(textsOf(await paragraph(8), '.para__cites .chip')).toEqual(['R-115', 'R-116'])
    expect(textsOf(await paragraph(9), '.para__cites .chip')).toEqual(['R-900'])
  })

  // "provenance runs both ways": a paragraph's rule opens that rule in the decision table
  it("opens a rule from its chip under the paragraph it cites, in the policy's rule set", async () => {
    const onOpenRules = vi.fn()
    renderScreen(onOpenRules)

    await userEvent.click(within(await paragraph(1)).getByRole('button', { name: 'R-110' }))

    expect(onOpenRules).toHaveBeenCalledWith(SEEDED_RULESET_ID, 'R-110')
  })

  // The spec, section 10: "and the findings that name it". Expected: the paragraphs of each planted finding in
  // fixtures/eval/policies/consumer-lending/seeded.findings.json, each mark with its code for the eye, as the spec's
  // composed screen writes it, and its kind's word for a screen reader and in its title
  it('marks under each paragraph the findings of the review that name it', async () => {
    serveReviewedDraft()
    renderScreen()

    await waitFor(async () =>
      expect(textsOf(await paragraph(4), '.para__cites .sev')).toEqual([
        'F-1 Ambiguity',
        'F-3 Unsupported',
      ]),
    )
    expect(textsOf(await paragraph(1), '.para__cites .sev')).toEqual([
      'F-2 Conflict',
      'F-5 Duplicate',
    ])
    expect(textsOf(await paragraph(3), '.para__cites .sev')).toEqual(['F-4 Gap'])
    expect(textsOf(await paragraph(8), '.para__cites .sev')).toEqual(['F-2 Conflict'])
    expect(textsOf(await paragraph(2), '.para__cites .sev')).toEqual([])
    const conflict = (await paragraph(8)).querySelector('.sev')!
    expect(conflict.firstChild).toHaveTextContent(/^F-2$/)
    expect(conflict).toHaveAttribute('title', 'F-2 Conflict')
    // the marks follow the rules that cite the paragraph
    expect(
      [...(await paragraph(4)).querySelectorAll('.para__cites > *')].map((one) => one.className),
    ).toEqual(['chip chip--id', 'chip chip--id', 'sev sev--warning', 'sev sev--error'])
  })

  // The spec, section 10: "The model's draft is announced by a dashed note with the model's mark and one button"
  it("announces the model's draft with the dashed note, the model's mark and Review the draft", async () => {
    const onOpenRules = vi.fn()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 9 }],
          ['draft', reviewedDraft],
        ]),
      ),
    )
    renderScreen(onOpenRules)

    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))

    const review = await screen.findByRole('button', { name: 'Review the draft' })
    const note = document.querySelector<HTMLElement>('.note.note--proposal')!
    expect(note.textContent).toBe(
      'A draft rule set was written from this policy: 20 rules, version 2. Nothing decides cases until a person publishes it.',
    )
    expect(note.querySelector('.actor--model')).not.toBeNull()
    // one primary per screen: the header's Generate rules; the note's button is secondary
    expect(review).toHaveClass('btn', 'btn--secondary')
    await userEvent.click(review)
    expect(onOpenRules).toHaveBeenCalledWith(SEEDED_RULESET_ID)
  })

  // The spec, section 10: "The margin holds the documents"; Documents, "seeded first, then this sandbox's"
  it('lists the documents in the margin, the seeded one first and marked Seeded', async () => {
    server.use(
      // the sandbox's own policy comes first from the API, and the margin still lists the seeded one first; MSW takes
      // the first match, so this stands in front of the two-rule-set handlers
      http.get(`${BASE}/policies`, () =>
        HttpResponse.json({
          policies: [
            {
              id: SECOND_POLICY_ID,
              title: secondPolicy.title,
              language: 'en',
              protected: false,
              versionNo: 1,
              paragraphs: 7,
              createdAt: '2026-09-20T15:40:00Z',
            },
            policies.policies[0]!,
          ],
        }),
      ),
      ...twoRulesets(),
    )
    renderScreen()

    const documents = await screen.findByRole('region', { name: 'Documents' })
    expect(within(documents).getByText("seeded first, then this sandbox's")).toBeInTheDocument()
    const rows = await within(documents).findAllByRole('button')
    expect(rows.map((row) => row.querySelector('.doc-row__name')!.textContent)).toEqual([
      seededPolicy.title,
      secondPolicy.title,
    ])
    expect(textsOf(rows[0]!, '.doc-row__meta > span')).toEqual([
      `${String(lendingParagraphs.length)} paragraphs`,
      'Hebrew',
      'Seeded',
    ])
    expect(rows[0]!.querySelector('.vstatus--seeded')).toHaveTextContent(/^Seeded$/)
    // a document of the sandbox's own says when it was added, on the 24-hour clock
    expect(textsOf(rows[1]!, '.doc-row__meta > span')).toEqual([
      '7 paragraphs',
      'English',
      'added 15:40',
    ])
    // the open document is the current row
    expect(rows[0]).toHaveAttribute('aria-current', 'true')
    expect(rows[1]).not.toHaveAttribute('aria-current')
  })

  // The owner's answer of 2026-09-28 to the phase's first question: each stage with its count, and the run's time
  it("shows the generation's four stages in the margin, each with its count, and the run's time", async () => {
    let now = 1_000
    vi.spyOn(performance, 'now').mockImplementation(() => now)
    let finish: () => void = () => undefined
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamed((send, close) => {
          send('parsing', { paragraphs: 9 })
          send('authoring', { paragraphs: 9 })
          send('validating', { paragraphs: 9 })
          send('reviewing', { paragraphs: 9 })
          finish = () => {
            now = 7_900
            send('draft', reviewedDraft)
            close()
          }
        }),
      ),
    )
    renderScreen()

    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))
    const generation = await screen.findByRole('region', { name: 'Generation' })
    await waitFor(() =>
      expect(within(generation).getByText(STAGE_REVIEWING).closest('.progress__step')).toHaveClass(
        'progress__step--now',
      ),
    )
    act(() => finish())

    await waitFor(() => expect(within(generation).getByText('done · 6.9 s')).toBeInTheDocument())
    const steps = [...generation.querySelectorAll('.progress__step')]
    expect(steps.map((step) => step.firstChild!.nextSibling!.textContent)).toEqual([
      'Reading the policy',
      'Writing the rules',
      'Checking every rule against the policy',
      STAGE_REVIEWING,
    ])
    // Expected: the policy's 9 paragraphs, the lending rule set's 20 rules, the validator's findings on the draft (none)
    // and the five planted findings of the review
    expect(steps.map((step) => step.querySelector('.progress__meta')?.textContent)).toEqual([
      '9 ¶',
      '20 rules',
      '0 problems',
      '5 findings',
    ])
    expect(steps.every((step) => step.classList.contains('progress__step--done'))).toBe(true)
  })

  // The spec, section 10: "The reviewer found 10 things to check · 7 block publishing", its first three and "All 10, in
  // the review". Expected: the five planted findings, three of which block publishing (Document 2, Flow 1)
  it('sums up the review in the margin: what it found, what blocks publishing, and the way to all of it', async () => {
    const onOpenRules = vi.fn()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 9 }],
          ['draft', reviewedDraft],
        ]),
      ),
    )
    renderScreen(onOpenRules)

    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))

    const summary = await screen.findByRole('region', {
      name: 'The reviewer found 5 things to check',
    })
    expect(within(summary).getByText('3 block publishing')).toBeInTheDocument()
    const findings = [...summary.querySelectorAll<HTMLElement>('.finding')]
    expect(findings.map((one) => textsOf(one, '.finding__head > span'))).toEqual([
      ['F-1', 'Ambiguity'],
      ['F-2', 'Conflict'],
      ['F-3', 'Unsupported'],
    ])
    expect(findings.map((one) => one.querySelector('.finding__claim')!.textContent)).toEqual(
      seededReview.findings.slice(0, 3).map((one) => one.message),
    )
    expect(findings[0]!.querySelector('.finding__claim')).toHaveAttribute('dir', 'rtl')
    await userEvent.click(within(summary).getByRole('button', { name: 'All 5, in the review' }))
    expect(onOpenRules).toHaveBeenCalledWith(SEEDED_RULESET_ID)
  })

  // The spec, section 10: '"Add policy" opens the form of section 05 in the margin with its Paste / Upload modes'
  it('opens the form of section 05 in the margin from Add policy, with Paste text and Upload a file', async () => {
    renderScreen()

    const add = await screen.findByRole('button', { name: 'Add policy' })
    expect(add).toHaveClass('btn', 'btn--secondary')
    await userEvent.click(add)

    const margin = screen.getByRole('complementary')
    const form = within(margin).getByRole('region', { name: 'Add a policy' })
    const modes = within(form).getByRole('group', { name: 'How to add the policy' })
    expect(within(modes).getByRole('button', { name: 'Paste text' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(within(modes).getByRole('button', { name: 'Upload a file' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(within(form).getByLabelText('Policy text')).toHaveClass('textarea', 'textarea--doc')
    await userEvent.click(within(modes).getByRole('button', { name: 'Upload a file' }))
    expect(within(form).getByLabelText('Policy file')).toHaveAttribute('type', 'file')
  })

  // The spec, section 10: the header's provenance names what is open
  it('counts the documents in the header and names the open one: read-only, its paragraphs, its language', async () => {
    renderScreen()

    await screen.findByText(lendingParagraphs[0]!.text)
    const line = document.querySelector<HTMLElement>('.ws-header .prov')!
    expect([...line.children].map((segment) => segment.textContent)).toEqual([
      '1 document',
      'Seeded · read-only',
      '9 paragraphs',
      'Hebrew',
      'every rule cites one of them',
    ])
    const actions = document.querySelector<HTMLElement>('.ws-header__side')!
    expect(
      within(actions)
        .getAllByRole('button')
        .map((one) => one.textContent),
    ).toEqual(['Add policy', 'Generate rules'])
    expect(within(actions).getByRole('button', { name: 'Generate rules' })).toHaveClass(
      'btn--primary',
    )
    // the sheet's title row: the document's name in its own direction, its version and size, and its rules
    const sheet = screen.getByRole('region', { name: seededPolicy.title })
    expect(within(sheet).getByText('Version 1 · 9 paragraphs')).toBeInTheDocument()
  })

  it('pastes a policy and sends the title, language and text', async () => {
    let sent: Record<string, unknown> | null = null
    server.use(
      http.post(`${BASE}/policies`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...emptyPolicy, id: 'new-policy' }, { status: 201 })
      }),
    )
    renderScreen()

    await userEvent.click(await screen.findByRole('button', { name: 'Add policy' }))
    await userEvent.type(screen.getByLabelText('Title'), 'Rental deposits')
    await userEvent.type(
      screen.getByLabelText('Policy text'),
      'Deposits are returned within 30 days.',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Add policy' }))

    await waitFor(() => expect(sent).not.toBeNull())
    expect(sent).toMatchObject({ title: 'Rental deposits', language: 'he' })
  })

  it('opens the rules of the policy on the screen', async () => {
    const onOpenRules = vi.fn()
    renderScreen(onOpenRules)

    await userEvent.click(await screen.findByRole('button', { name: 'Open its rules' }))

    // which rule set, not just that something opened: day 8 found the screen opening whichever came first
    expect(onOpenRules).toHaveBeenCalledWith(SEEDED_RULESET_ID)
  })

  it('opens the rule set of the policy the reader chose, not the first in the sandbox', async () => {
    const onOpenRules = vi.fn()
    server.use(...twoRulesets())
    renderScreen(onOpenRules)

    await userEvent.click(await screen.findByRole('button', { name: /Security Deposit/ }))
    await userEvent.click(await screen.findByRole('button', { name: 'Open its rules' }))

    expect(onOpenRules).toHaveBeenCalledWith(SECOND_RULESET_ID)
  })

  it('reviewing a draft opens the rule set the generation just wrote', async () => {
    const onOpenRules = vi.fn()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 9 }],
          ['authoring', { paragraphs: 9 }],
          ['validating', { paragraphs: 9 }],
          ['draft', secondVersion],
        ]),
      ),
    )
    renderScreen(onOpenRules)

    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Review the draft' }))

    expect(onOpenRules).toHaveBeenCalledWith(SECOND_RULESET_ID)
  })

  // Document 4, Field hints: step 1 generates the pasted sample with the seeded rule set's inputs, the fields the 200
  // cases supply. Expected: the header line, the lending fixture's first and last inputs, and its nine inputs only
  it("step 1 generates the pasted sample with the seeded rule set's inputs as field hints", async () => {
    let sent: Record<string, unknown> | null = null
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return new HttpResponse('event:parsing\ndata:{}\n\n', {
          headers: { 'Content-Type': 'text/event-stream' },
        })
      }),
    )
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <PoliciesScreen onOpenRules={() => undefined} demoAsked onDemoHandled={() => undefined} />
      </QueryClientProvider>,
    )

    await waitFor(() => expect(screen.getByLabelText('Policy text')).not.toHaveValue(''))
    await userEvent.click(screen.getByRole('button', { name: 'Add policy' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))

    await waitFor(() => expect(sent).not.toBeNull())
    // TypeScript narrows `sent` to null here, not seeing the handler assign it
    const body = sent as Record<string, unknown> | null
    const hints = String(body?.hints).split('\n')
    expect(hints[0]).toBe('The application supplies these inputs:')
    expect(hints[1]).toBe('- age (integer, years)')
    expect(hints[hints.length - 1]).toBe('- has_guarantor (boolean)')
    expect(hints).toHaveLength(10)
  })

  // Document 4: hints are the analyst's, and a person pasting a policy gives none here. Expected: an empty body
  it('generates a policy a person chose without hints', async () => {
    let sent: Record<string, unknown> | null = null
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return new HttpResponse('event:parsing\ndata:{}\n\n', {
          headers: { 'Content-Type': 'text/event-stream' },
        })
      }),
    )
    renderScreen()

    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))

    await waitFor(() => expect(sent).not.toBeNull())
    expect(sent).toEqual({})
  })

  // Day 15, on the live site: while a new policy is being added, the Generate button still belongs to the policy
  // that was open, so a quick click generated the wrong one. Expected: disabled until the new policy is added
  it('does not generate while a new policy is still being added', async () => {
    let release: () => void = () => undefined
    const added = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.post(`${BASE}/policies`, async () => {
        await added
        return HttpResponse.json({ ...emptyPolicy, id: 'new-policy' }, { status: 201 })
      }),
    )
    renderScreen()
    await screen.findByRole('button', { name: 'Generate rules' })

    await userEvent.click(screen.getByRole('button', { name: 'Add policy' }))
    await userEvent.type(screen.getByLabelText('Title'), 'Rental deposits')
    await userEvent.type(
      screen.getByLabelText('Policy text'),
      'Deposits are returned within 30 days.',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Add policy' }))

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Generate rules' })).toBeDisabled(),
    )
    // the reason stands beside the disabled primary (the spec, section 05)
    expect(document.querySelector('.ws-header__side .reason')).toHaveTextContent(
      'The new policy is still being added',
    )
    release()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Generate rules' })).toBeEnabled(),
    )
  })

  // Document 4, Field hints: step 1's generation carries the seeded inputs. Expected: the pasted policy cannot be
  // generated until the seeded version is read, and then goes out with the hints
  it("step 1 waits for the seeded rule set's inputs before it can generate", async () => {
    let release: () => void = () => undefined
    const read = new Promise<void>((resolve) => {
      release = resolve
    })
    let sent: Record<string, unknown> | null = null
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:no`, async () => {
        await read
        return HttpResponse.json(publishedVersion)
      }),
      http.post(`${BASE}/policies/:id/rulesets`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return new HttpResponse('event:parsing\ndata:{}\n\n', {
          headers: { 'Content-Type': 'text/event-stream' },
        })
      }),
    )
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <PoliciesScreen onOpenRules={() => undefined} demoAsked onDemoHandled={() => undefined} />
      </QueryClientProvider>,
    )

    await waitFor(() => expect(screen.getByLabelText('Policy text')).not.toHaveValue(''))
    await userEvent.click(screen.getByRole('button', { name: 'Add policy' }))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Generate rules' })).toBeDisabled(),
    )
    release()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Generate rules' })).toBeEnabled(),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Generate rules' }))

    await waitFor(() => expect(sent).not.toBeNull())
    // TypeScript narrows `sent` to null here, not seeing the handler assign it
    const body = sent as Record<string, unknown> | null
    expect(String(body?.hints)).toMatch(
      /^The application supplies these inputs:\n- age \(integer, years\)/,
    )
  })

  it('shows the error code when the list cannot be read', async () => {
    server.use(
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
    renderScreen()

    expect(await screen.findByText('SESSION_INVALID')).toBeInTheDocument()
  })
})

/** The Policies row of the states matrix (the spec, section 11), one test per cell no other test covers. */
describe('PoliciesScreen, every state', () => {
  it('Policies · loading', async () => {
    let release: () => void = () => undefined
    const read = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.get(`${BASE}/policies/:id`, async () => {
        await read
        return HttpResponse.json(seededPolicy)
      }),
    )
    renderScreen()

    // still rows and one line of text; the header and the margin are there at once (the spec, section 08)
    const loading = await screen.findByText('Loading the policy')
    expect(loading.closest('.loading')!.querySelectorAll('.loading__row')).toHaveLength(3)
    expect(screen.getByRole('heading', { level: 1, name: 'Policies' })).toBeInTheDocument()
    expect(await screen.findByRole('region', { name: 'Documents' })).toBeInTheDocument()
    release()
    expect(await screen.findByText(lendingParagraphs[0]!.text)).toBeInTheDocument()
    expect(screen.queryByText('Loading the policy')).not.toBeInTheDocument()
  })

  // "Generate rules → four stages, each with its count, and the run's time → the dashed draft note → Review the draft"
  it('Policies · working', async () => {
    const onOpenRules = vi.fn()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 9 }],
          ['authoring', { paragraphs: 9 }],
          ['validating', { paragraphs: 9 }],
          ['reviewing', { paragraphs: 9 }],
          ['draft', reviewedDraft],
        ]),
      ),
    )
    renderScreen(onOpenRules)

    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))

    const generation = await screen.findByRole('region', { name: 'Generation' })
    await waitFor(() =>
      expect(generation.querySelectorAll('.progress__step--done')).toHaveLength(4),
    )
    expect(within(generation).getByText(/^done · /)).toBeInTheDocument()
    expect(document.querySelector('.note--proposal')).toHaveTextContent(
      'A draft rule set was written from this policy: 20 rules, version 2.',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Review the draft' }))
    expect(onOpenRules).toHaveBeenCalledWith(SEEDED_RULESET_ID)
  })

  it('Policies · empty', async () => {
    server.use(
      http.get(`${BASE}/policies`, () => HttpResponse.json({ policies: [] })),
      http.get(`${BASE}/rulesets`, () => HttpResponse.json({ rulesets: [] })),
    )
    renderScreen()

    // "No policy open" on the ruled lines, and the one action that would put one here
    const sentence = await screen.findByText('No policy open')
    expect(sentence).toHaveClass('empty__rule', 'empty__rule--text')
    const empty = sentence.closest<HTMLElement>('.empty')!
    await userEvent.click(within(empty).getByRole('button', { name: 'Add policy' }))
    expect(
      within(screen.getByRole('complementary')).getByRole('region', { name: 'Add a policy' }),
    ).toBeInTheDocument()
    // nothing is open, so nothing can be generated
    expect(screen.queryByRole('button', { name: 'Generate rules' })).not.toBeInTheDocument()
  })

  // The spec, section 08's refusal: the code, a row per pointer and problem, the closing fact, and what the model
  // proposed behind a link. Expected: the author prompt's two repairs (Document 4, Repair Loop: "two repairs for author")
  it('Policies · RULESET_INVALID', async () => {
    const proposed = { ...lendingRuleSet, id: 'the-model-answer' }
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 9 }],
          ['authoring', { paragraphs: 9 }],
          ['validating', { paragraphs: 9 }],
          [
            'error',
            {
              code: 'RULESET_INVALID',
              findings: [
                {
                  code: 'BETWEEN_RANGE_INVALID',
                  severity: 'error',
                  path: '/rules/7/condition',
                  message: 'the lower bound 150,000 is above the upper bound 10,000',
                  ruleIds: ['R-120'],
                  fieldNames: [],
                },
                {
                  code: 'ENUM_VALUE_UNKNOWN',
                  severity: 'error',
                  path: '/rules/12/actions/0',
                  message: '"self-employed" is not a value of employment_type',
                  ruleIds: ['R-160'],
                  fieldNames: ['employment_type'],
                },
              ],
              document: proposed,
            },
          ],
        ]),
      ),
    )
    renderScreen()

    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))

    const refusal = await screen.findByRole('alert')
    expect(refusal.querySelector('.refusal__head')!.textContent).toBe(
      'RULESET_INVALIDNo rule set was written.',
    )
    expect(textsOf(refusal, 'dt')).toEqual(['/rules/7/condition', '/rules/12/actions/0'])
    expect(textsOf(refusal, 'dd')).toEqual([
      'BETWEEN_RANGE_INVALID · the lower bound 150,000 is above the upper bound 10,000',
      'ENUM_VALUE_UNKNOWN · "self-employed" is not a value of employment_type',
    ])
    expect(refusal.querySelector('.refusal__foot')!.textContent).toBe(
      'The model was asked twice to repair the draft; both attempts failed the validator. Nothing was stored. What the model proposed',
    )
    expect(screen.queryByRole('button', { name: 'Review the draft' })).not.toBeInTheDocument()
    const link = within(refusal).getByRole('button', { name: 'What the model proposed' })
    expect(link).toHaveAttribute('aria-expanded', 'false')
    await userEvent.click(link)
    expect(link).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByLabelText('What the model proposed')).toHaveTextContent(
      '"id": "the-model-answer"',
    )
    // the stages stop where the refusal came: the checking stage ended it, and has no mark
    const steps = [
      ...screen.getByRole('region', { name: 'Generation' }).querySelectorAll('.progress__step'),
    ]
    expect(steps.map((step) => step.className)).toEqual([
      'progress__step progress__step--done',
      'progress__step progress__step--done',
      'progress__step',
      'progress__step',
    ])
  })

  // The owner's answer of 2026-09-28 to the phase's third question: the code Document 2 names for the text's limits
  it('Policies · POLICY_INVALID', async () => {
    server.use(
      http.post(`${BASE}/policies`, () =>
        HttpResponse.json(
          { code: 'POLICY_INVALID', message: 'refused', details: [], traceId: 't' },
          { status: 422 },
        ),
      ),
    )
    renderScreen()

    await userEvent.click(await screen.findByRole('button', { name: 'Add policy' }))
    await userEvent.type(screen.getByLabelText('Title'), 'Too long')
    await userEvent.type(screen.getByLabelText('Policy text'), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Add policy' }))

    const refusal = await screen.findByRole('alert')
    expect(refusal).toHaveTextContent(
      'The text is over its limits (40 KB, 200 paragraphs, 4,000 characters a paragraph) or holds a control character.',
    )
    expect(within(refusal).getByText('POLICY_INVALID')).toHaveClass('mono')
    expect(screen.getByLabelText('Policy text')).toHaveAttribute('aria-invalid', 'true')
  })

  // The owner's answer of 2026-09-28 to the phase's third question: the code Document 2 names for a file over 2 MB
  it('Policies · PAYLOAD_TOO_LARGE', async () => {
    server.use(
      http.post(`${BASE}/policies`, () =>
        HttpResponse.json(
          { code: 'PAYLOAD_TOO_LARGE', message: 'refused', details: [], traceId: 't' },
          { status: 413 },
        ),
      ),
    )
    renderScreen()

    await userEvent.click(await screen.findByRole('button', { name: 'Add policy' }))
    await userEvent.click(screen.getByRole('button', { name: 'Upload a file' }))
    await userEvent.type(screen.getByLabelText('Title'), 'A large policy')
    await userEvent.upload(
      screen.getByLabelText('Policy file'),
      new File(['text'], 'policy.pdf', { type: 'application/pdf' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Add policy' }))

    const refusal = await screen.findByRole('alert')
    expect(refusal).toHaveTextContent('The file is larger than the 2 MB the API accepts.')
    expect(within(refusal).getByText('PAYLOAD_TOO_LARGE')).toHaveClass('mono')
  })

  it('Policies · PROVIDER_UNAVAILABLE', async () => {
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 9 }],
          ['error', { code: 'PROVIDER_UNAVAILABLE', findings: [], document: null }],
        ]),
      ),
    )
    renderScreen()

    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))

    const refusal = await screen.findByRole('alert')
    expect(refusal.querySelector('.refusal__head')!.textContent).toBe(
      'PROVIDER_UNAVAILABLENo rule set was written.',
    )
    expect(refusal.querySelector('.refusal__foot')!.textContent).toBe(
      'The model did not answer in time. Try again. Nothing was stored.',
    )
    expect(within(refusal).queryByRole('button')).not.toBeInTheDocument()
  })

  // Document 2: a model-calling route answers 429 RATE_LIMITED before any stream opens; the day-15 hook called every
  // failure PROVIDER_UNAVAILABLE
  it('Policies · RATE_LIMITED', async () => {
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        HttpResponse.json(
          { code: 'RATE_LIMITED', message: 'Too many requests.', details: [], traceId: 't' },
          { status: 429, headers: { 'Retry-After': '60' } },
        ),
      ),
    )
    renderScreen()

    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))

    const refusal = await screen.findByRole('alert')
    expect(refusal.querySelector('.refusal__head')!.textContent).toBe(
      'RATE_LIMITEDNo rule set was written.',
    )
    expect(refusal.querySelector('.refusal__foot')!.textContent).toBe(
      'Too many requests in a short time. Wait a minute, then try again. Nothing was stored.',
    )
  })

  it('Policies · seeded, read-only', async () => {
    server.use(...twoRulesets())
    renderScreen()

    await screen.findByText(lendingParagraphs[0]!.text)
    const line = () => document.querySelector<HTMLElement>('.ws-header .prov')!
    expect(line().querySelector('.vstatus.vstatus--seeded')).toHaveTextContent(
      /^Seeded · read-only$/,
    )
    // a document of the sandbox's own carries no such status
    await userEvent.click(screen.getByRole('button', { name: /Security Deposit/ }))
    await waitFor(() => expect(line().querySelector('.vstatus')).toBeNull())
  })

  it('Policies · Generate rules for this policy first', async () => {
    server.use(
      // the policy was added but never generated from, so no rule set cites it; MSW takes the first match,
      // so this stands in front of the two-rule-set handlers
      http.get(`${BASE}/rulesets`, () => HttpResponse.json({ rulesets: [] })),
      ...twoRulesets(),
    )
    renderScreen()

    const open = await screen.findByRole('button', { name: 'Open its rules' })
    expect(open).toBeDisabled()
    // the reason is text beside the disabled button, never a tooltip (the spec, section 05), before it as the header's is
    expect(open.previousElementSibling).toHaveClass('reason')
    expect(open.previousElementSibling).toHaveTextContent(/^Generate rules for this policy first$/)
  })
})

/** Reviewing's label, which the stage list and the tests share with the hook. */
const STAGE_REVIEWING = 'Reviewing the draft for gaps, conflicts and ambiguities'

const emptyPolicy = {
  id: 'new-policy',
  title: 'Rental deposits',
  language: 'en',
  protected: false,
  createdAt: '2026-09-20T10:00:00Z',
  versions: [
    {
      versionNo: 1,
      createdAt: '2026-09-20T10:00:00Z',
      paragraphs: [{ index: 1, text: 'Deposits.' }],
    },
  ],
}

describe('PoliciesScreen in both directions (NFR-5)', () => {
  it('RTL: the Hebrew policy reads right to left inside the English screen (snapshot)', async () => {
    renderScreen()

    const first = await screen.findByText(lendingParagraphs[0]!.text)
    expect(first.closest('[dir]')).toHaveAttribute('dir', 'rtl')
    expect(rtlSnapshot(first.closest('section')!)).toMatchSnapshot()
  })

  it('LTR: an English policy reads left to right in the same screen (snapshot)', async () => {
    server.use(
      http.get(`${BASE}/policies`, () =>
        HttpResponse.json({
          policies: [
            {
              id: englishPolicy.id,
              title: englishPolicy.title,
              language: 'en',
              protected: false,
              versionNo: 1,
              paragraphs: englishParagraphs.length,
              createdAt: englishPolicy.createdAt,
            },
          ],
        }),
      ),
      http.get(`${BASE}/policies/:id`, () => HttpResponse.json(englishPolicy)),
      http.get(`${BASE}/rulesets`, () => HttpResponse.json({ rulesets: [] })),
    )
    renderScreen()

    const first = await screen.findByText(englishParagraphs[0]!.text)
    expect(first.closest('[dir]')).toHaveAttribute('dir', 'ltr')
    expect(first.closest('[lang]')).toHaveAttribute('lang', 'en')
    expect(rtlSnapshot(first.closest('section')!)).toMatchSnapshot()
  })
})

describe('PoliciesScreen.css', () => {
  it("carries the spec's document list, with the spec's declarations", () => {
    const documents = specRules('/* Policy document and its list */', '/* Access gate').filter(
      ([selector]) => selector.startsWith('.doc'),
    )

    expect(documents).toHaveLength(6)
    expect(unported(stylesheet('features/policy/PoliciesScreen.css'), documents)).toEqual([])
  })
})
