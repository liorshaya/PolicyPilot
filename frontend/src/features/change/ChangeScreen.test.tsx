import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { specRules, stylesheet, unported } from '../../test/css'
import {
  approvedDecision,
  PROPOSAL_ID,
  proposedSampleDecision,
  rt04Events,
  scriptedEvents,
  scriptedProposalEvent,
  scriptedRequest,
} from '../../test/fixtures/change'
import { eventStream, RT_04_PLANTED } from '../../test/fixtures/changeRequest'
import { decisionIdOf, lendingRuleSet, sampleDecision } from '../../test/fixtures/lending'
import {
  publishedVersion,
  rulesets,
  SECOND_RULESET_ID,
  SEEDED_RULESET_ID,
  twoRulesets,
} from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { ChangeScreen } from './ChangeScreen'

// @requirement FR-17
// @requirement FR-18
// @requirement FR-19

/**
 * The change screen (Brief, demo step 4: "The agent lists the two affected rules, shows a diff, reruns the 200 cases: 12
 * decisions flip, listed by id. Click Approve. Version 2 is published"), drawn as the Register draws it (the spec,
 * section 09, "The change request": the request with its counter and the base version beside the primary, the four
 * stages with the rules considered under the first, the proposal as patches with the model's rationale, the diff, the
 * regression, the decision with its note, then the seal; a refusal is the refusal block of section 08 with "What the
 * model proposed" behind a link). MSW answers with the streams the API sends, built from the committed fixtures.
 */

const BASE = 'http://localhost:8080/api/v1'
const TEXT = scriptedRequest.text.he

const streamed = (events: [string, unknown][]) =>
  new HttpResponse(eventStream(events), { headers: { 'Content-Type': 'text/event-stream' } })

function renderScreen(rulesetId: string | null = SEEDED_RULESET_ID) {
  const onPublished = vi.fn()
  const onOpenAudit = vi.fn()
  const onOpenRules = vi.fn()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ChangeScreen
        rulesetId={rulesetId}
        onPublished={onPublished}
        onOpenAudit={onOpenAudit}
        onOpenRules={onOpenRules}
      />
    </QueryClientProvider>,
  )
  return { onPublished, onOpenAudit, onOpenRules }
}

async function propose(text = TEXT) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('What should change'), text)
  // the button waits for the text and for the version the change is proposed on
  await waitFor(() =>
    expect(screen.getByRole('button', { name: 'Propose the change' })).toBeEnabled(),
  )
  await user.click(screen.getByRole('button', { name: 'Propose the change' }))
  return user
}

async function proposalShown() {
  const user = await propose()
  await screen.findByRole('list', { name: 'Patches' })
  return user
}

/** A stage of the progress, found by its words. */
function stage(label: string): HTMLElement {
  return within(screen.getByRole('group', { name: 'Progress' }))
    .getByText(label)
    .closest<HTMLElement>('.progress__step')!
}

/** The identifiers a group of chips holds, each with whether it is faded as left unchanged. */
function chipsOf(group: string): [string, boolean][] {
  return [...screen.getByRole('group', { name: group }).querySelectorAll('.chip')].map((chip) => [
    chip.textContent ?? '',
    chip.getAttribute('data-state') === 'unchanged',
  ])
}

/** The segments of a provenance line, as a reader sees them between the drawn separators. */
function segmentsOf(line: Element): string[] {
  return [...line.children].map((segment) => segment.textContent ?? '')
}

describe('ChangeScreen, the request', () => {
  it('proposes on the latest published version of the rule set, falling back to the seeded one', async () => {
    const sent: string[] = []
    server.use(
      ...twoRulesets(),
      http.post(`${BASE}/rulesets/:id/versions/:no/changes`, ({ params }) => {
        sent.push(`${String(params.id)}/${String(params.no)}`)
        return streamed(scriptedEvents)
      }),
    )
    // the workspace is on a rule set whose only version is a draft, written a moment ago
    renderScreen(SECOND_RULESET_ID)

    const note = await screen.findByText(
      'The rule set on the workspace has no published version yet; the change is proposed on the seeded one.',
    )
    // the spec, section 08: a note carries no fill, only its bar and the system's mark
    expect(note.closest('.note')?.querySelector('.actor--system')).not.toBeNull()
    await proposalShown()

    expect(sent).toStrictEqual([`${SEEDED_RULESET_ID}/1`])
  })

  it("takes the request in the policy's terms with its counter, and states the base version beside the primary", async () => {
    renderScreen()
    const user = userEvent.setup()

    const field = await screen.findByLabelText('What should change')
    expect(field).toHaveClass('textarea', 'textarea--he')
    expect(field).toHaveAttribute('dir', 'auto')
    expect(field).toHaveAttribute('maxlength', '1000')
    expect(
      screen.getByText(
        "In the policy's own terms. The model proposes; the engine decides this sandbox's cases again; a person approves.",
      ),
    ).toHaveClass('field__hint')
    expect(screen.getByText('0 / 1,000')).toHaveClass('counter')

    await user.type(field, TEXT)

    // the scripted request is 40 characters; the spec's frame writes 41 beside the same sentence
    expect(screen.getByText('40 / 1,000')).toHaveClass('counter')
    const primary = screen.getByRole('button', { name: 'Propose the change' })
    expect(primary).toHaveClass('btn--primary')
    const reason = await screen.findByText('Published v1')
    expect(reason).toHaveClass('mono')
    expect(reason.closest('.reason')).toHaveTextContent('On Published v1')
    expect(reason.closest('.reason')?.nextElementSibling).toBe(primary)
  })

  it('fills in the scripted request when the guided panel runs step 4, once', async () => {
    const onDemoHandled = vi.fn()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <ChangeScreen rulesetId={SEEDED_RULESET_ID} demoAsked onDemoHandled={onDemoHandled} />
      </QueryClientProvider>,
    )

    // the panel types what a presenter would, and leaves the proposing to them (Document 2, the guided panel)
    expect(await screen.findByLabelText('What should change')).toHaveValue(TEXT)
    expect(onDemoHandled).toHaveBeenCalledOnce()
    expect(screen.queryByRole('group', { name: 'Progress' })).not.toBeInTheDocument()
  })
})

describe('ChangeScreen, the four stages', () => {
  it('shows each stage as it runs, then done with its time and its tokens, and the rules considered under the first', async () => {
    const encoder = new TextEncoder()
    let stream: ReadableStreamDefaultController<Uint8Array> | null = null
    server.use(
      http.post(
        `${BASE}/rulesets/:id/versions/:no/changes`,
        () =>
          new HttpResponse(
            new ReadableStream<Uint8Array>({
              start(controller) {
                stream = controller
              },
            }),
            { headers: { 'Content-Type': 'text/event-stream' } },
          ),
      ),
    )
    renderScreen()
    await propose()
    const send = (events: [string, unknown][]) =>
      stream!.enqueue(encoder.encode(eventStream(events)))

    send(scriptedEvents.slice(0, 1))
    await waitFor(() =>
      expect(stage('Finding the rules the request touches')).toHaveClass('progress__step--now'),
    )
    expect(stage('Finding the rules the request touches')).toHaveAttribute('aria-current', 'step')
    expect(stage('Writing the patches')).not.toHaveClass('progress__step--now')
    expect(stage('Writing the patches')).not.toHaveClass('progress__step--done')

    send(scriptedEvents.slice(1, 2))
    await waitFor(() => expect(stage('Writing the patches')).toHaveClass('progress__step--now'))
    // the spec's frame: "0.8 s" for 812 ms, the time the proposing event says the first stage took
    expect(stage('Finding the rules the request touches')).toHaveClass('progress__step--done')
    expect(stage('Finding the rules the request touches')).toHaveTextContent(/0\.8 s$/)
    // the Work Plan's five candidates of the scripted request, all solid until the model has answered
    expect(chipsOf('Rules considered')).toStrictEqual([
      ['R-170', false],
      ['R-410', false],
      ['R-020', false],
      ['R-200', false],
      ['R-320', false],
    ])

    send(scriptedEvents.slice(2, 4))
    await waitFor(() =>
      expect(stage("Deciding this sandbox's cases again")).toHaveClass('progress__step--now'),
    )
    expect(stage('Writing the patches')).toHaveTextContent(/4\.1 s · 2,140 tokens$/)
    expect(stage('Checking the patched rules against the policy')).toHaveTextContent(/38 ms$/)
    // the regression decides again every case the sandbox decided on version 1: its statistics count 200
    expect(stage("Deciding this sandbox's cases again")).toHaveTextContent(/200 cases$/)

    send(scriptedEvents.slice(4))
    stream!.close()
    await screen.findByRole('list', { name: 'Patches' })
    expect(stage("Deciding this sandbox's cases again")).toHaveClass('progress__step--done')
    expect(stage("Deciding this sandbox's cases again")).toHaveTextContent(/0\.9 s$/)
  })

  it('fades the rules the proposal left unchanged once it arrives, so its reach shows beside its result', async () => {
    renderScreen()
    await proposalShown()

    expect(chipsOf('Rules considered')).toStrictEqual([
      ['R-170', false],
      ['R-410', false],
      ['R-020', true],
      ['R-200', true],
      ['R-320', true],
    ])
  })
})

describe('ChangeScreen, the proposal', () => {
  it('lists the patches by operation, each with its rule and the rationale in Hebrew, then what was left unchanged', async () => {
    renderScreen()
    await proposalShown()

    const proposal = screen.getByRole('region', { name: 'Proposal' })
    expect(within(proposal).getByText('2 patches')).toBeVisible()
    // the spec, section 04: a proposal is dashed until a person decides, and the model's mark says who wrote it
    expect(within(proposal).getByText('Proposed', { selector: '.sec *' })).toHaveClass(
      'vstatus',
      'vstatus--pending',
    )
    expect(within(proposal).getByText('model')).toHaveClass('actor', 'actor--model')
    const patches = within(screen.getByRole('list', { name: 'Patches' })).getAllByRole('listitem')
    expect(patches).toHaveLength(2)
    const [r170, r410] = scriptedRequest.expected.patches
    expect(within(patches[0]!).getByText('Replace')).toHaveClass('patch__op')
    expect(within(patches[0]!).getByText('R-170')).toHaveClass('chip', 'chip--id')
    // the spec's frame: דחייה: הכנסה חודשית נטו נמוכה מ-<bdi>9,000</bdi>
    const label = patches[0]!.querySelector('.patch__head .step__label')
    expect(label).toHaveTextContent('דחייה: הכנסה חודשית נטו נמוכה מ-9,000')
    expect(label).toHaveAttribute('dir', 'rtl')
    expect(label?.querySelector('bdi')).toHaveTextContent('9,000')
    const rationale = patches[0]!.querySelector<HTMLElement>('.patch__rationale')!
    expect(rationale).toHaveTextContent(r170!.rationale)
    expect(rationale).toHaveAttribute('lang', 'he')
    // the bidi law, section 03: every number inside Hebrew is isolated
    expect([...rationale.querySelectorAll('bdi')].map((token) => token.textContent)).toStrictEqual([
      '8,000',
      '9,000',
    ])
    expect(within(patches[1]!).getByText('R-410')).toBeVisible()
    expect(patches[1]!.querySelector('.patch__rationale')).toHaveTextContent(r410!.rationale)
    expect(chipsOf('Considered and left unchanged')).toStrictEqual([
      ['R-020', true],
      ['R-200', true],
      ['R-320', true],
    ])
  })

  it("writes each operation in the spec's words: a removed rule under its label, a new field by its name", async () => {
    // a proposal the scripted request never makes, to show the operations it does not use (the spec, section 09)
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/changes`, () =>
        streamed([
          ...scriptedEvents.slice(0, 4),
          [
            'proposal',
            {
              ...scriptedProposalEvent,
              patches: [
                { op: 'remove', ruleId: 'R-100', rationale: 'הכלל מבוטל לפי הבקשה' },
                {
                  op: 'add_field',
                  field: { name: 'has_collateral', type: 'boolean' },
                  rationale: 'שדה חדש לבטוחה',
                },
              ],
            },
          ],
        ]),
      ),
    )
    renderScreen()
    await proposalShown()

    const patches = within(screen.getByRole('list', { name: 'Patches' })).getAllByRole('listitem')
    expect(patches.map((patch) => patch.querySelector('.patch__op')?.textContent)).toStrictEqual([
      'Remove',
      'Add the field',
    ])
    // a removed rule is named as version 1 has it (fixtures/.../ruleset.v1.json)
    const removed = lendingRuleSet.rules.find((rule) => rule.id === 'R-100')!
    expect(patches[0]!.querySelector('.patch__head .step__label')).toHaveTextContent(removed.label)
    expect(within(patches[1]!).getByText('has_collateral')).toHaveClass('chip', 'chip--field')
  })

  it('shows the diff, one row per changed cell of Published v1 beside the proposal, the other rules collapsed', async () => {
    renderScreen()
    await proposalShown()

    const diff = screen.getByRole('region', { name: 'Changes from Published v1 to Proposed' })
    const head = diff.querySelector('.udiff__row--head')!
    expect([...head.children].map((cell) => cell.textContent)).toStrictEqual([
      'Rule',
      'Field',
      'Published v1',
      '',
      'Proposed',
    ])
    // 20 rules in version 1, two of them changed
    expect(diff.querySelector('.udiff__row--collapsed')).toHaveTextContent(
      '18 unchanged rules · Show · Side by side',
    )
  })

  it("heads the regression with the engine's line, 200 decisions of v1 in 0.9 s, and lists the 12 flips", async () => {
    renderScreen()
    await proposalShown()

    const regression = screen.getByRole('region', { name: 'Regression' })
    expect(within(regression).getByText('engine · 200 decisions of v1 · 0.9 s')).toHaveClass(
      'actor',
      'actor--engine',
    )
    expect(within(regression).getByRole('button', { name: 'Show all 12' })).toBeVisible()
  })

  it("opens a flipped case's two traces in the margin: the stored one, then what the proposal decides", async () => {
    const asked: string[] = []
    // the API's answers are case 17's, the one decision the fixtures hold in full; under test is what the click asks
    // for and where the two traces land
    server.use(
      http.get(`${BASE}/decisions/:id`, ({ params }) => {
        asked.push(`stored ${String(params.id)}`)
        return HttpResponse.json(sampleDecision)
      }),
      http.get(`${BASE}/changes/:id/decisions/:decisionId/trace`, ({ params }) => {
        asked.push(`proposed ${String(params.id)} ${String(params.decisionId)}`)
        return HttpResponse.json(proposedSampleDecision)
      }),
    )
    renderScreen()
    const user = await proposalShown()
    const flips = within(screen.getByRole('table', { name: 'The decisions that flip' }))

    // case 8 is the first flip of cases-expected.json
    await user.click(flips.getAllByRole('button', { name: 'Both traces' })[0]!)

    const margin = await screen.findByRole('complementary', { name: 'Both traces' })
    await waitFor(() => expect(margin.querySelectorAll('.trace__head .prov')).toHaveLength(2))
    expect(asked.sort()).toStrictEqual([
      `proposed ${PROPOSAL_ID} ${decisionIdOf(8)}`,
      `stored ${decisionIdOf(8)}`,
    ])
    const [stored, proposed] = [...margin.querySelectorAll('.trace__head .prov')]
    expect(segmentsOf(stored!)[0]).toBe('decided on v1')
    expect(segmentsOf(proposed!)).toStrictEqual(['decided on CR-0001', 'engine', 'not stored'])

    await user.click(within(margin).getByRole('button', { name: 'Close' }))

    expect(screen.queryByRole('complementary', { name: 'Both traces' })).not.toBeInTheDocument()
  })

  it('says so in the margin when what the proposal decides cannot be read, and offers to try again', async () => {
    server.use(
      http.get(`${BASE}/changes/:id/decisions/:decisionId/trace`, () =>
        HttpResponse.json(
          { code: 'NOT_FOUND', message: 'no such decision', details: [], traceId: 't' },
          { status: 404 },
        ),
      ),
    )
    renderScreen()
    const user = await proposalShown()
    const flips = within(screen.getByRole('table', { name: 'The decisions that flip' }))

    await user.click(flips.getAllByRole('button', { name: 'Both traces' })[0]!)

    const margin = await screen.findByRole('complementary', { name: 'Both traces' })
    expect(
      await within(margin).findByText('What the proposal decides could not be read.'),
    ).toBeVisible()
    expect(within(margin).getByText('NOT_FOUND')).toHaveClass('mono')
    expect(within(margin).getByRole('button', { name: 'Try again' })).toBeVisible()
  })
})

describe('ChangeScreen, the decision', () => {
  it('offers the note, Reject in the danger style and "Approve and publish v2" as the primary, what it does beside it', async () => {
    renderScreen()
    await proposalShown()

    const note = screen.getByLabelText('Note for the audit log')
    expect(note).toHaveClass('textarea', 'textarea--he')
    expect(note).toHaveAttribute('dir', 'auto')
    expect(note).toHaveAttribute('maxlength', '1000')
    expect(screen.getByRole('button', { name: 'Reject' })).toHaveClass('btn--danger')
    const approve = screen.getByRole('button', { name: 'Approve and publish v2' })
    expect(approve).toHaveClass('btn--primary')
    // the spec's states table: a change on the seeded rule set publishes into the sandbox's copy, said beside the
    // primary
    const reason = screen.getByText("Approving publishes v2 in this sandbox's copy.")
    expect(reason).toHaveClass('reason')
    expect(reason.parentElement).toContainElement(approve)
  })

  it('approves with the note: the seal, the sentence, and the rules and the audit log one click away', async () => {
    const sent: unknown[] = []
    server.use(
      http.post(`${BASE}/changes/:id/approve`, async ({ request, params }) => {
        sent.push({ id: params.id, body: await request.json() })
        return HttpResponse.json(approvedDecision)
      }),
    )
    const { onPublished, onOpenAudit, onOpenRules } = renderScreen()
    const user = await proposalShown()

    await user.type(screen.getByLabelText('Note for the audit log'), 'אושר בוועדת האשראי')
    await user.click(screen.getByRole('button', { name: 'Approve and publish v2' }))

    expect(
      await screen.findByText(
        "Approved. Version 2 is published in this sandbox's own copy of the rule set; the cases decide on it from now on.",
      ),
    ).toBeVisible()
    expect(sent).toStrictEqual([{ id: PROPOSAL_ID, body: { note: 'אושר בוועדת האשראי' } }])
    expect(onPublished).toHaveBeenCalledWith(approvedDecision.result)
    // the spec, section 09: "CR-0001 · 2026-09-24 16:19", the request's number and when a person decided
    const seal = screen.getByText('Approved', { selector: '.seal__kicker' }).closest('.seal')!
    expect(seal.querySelector('.seal__line')).toHaveTextContent('CR-0001 · 2026-09-27 09:12')
    expect(seal.querySelector('.seal__by')).toHaveTextContent('by Analyst')
    expect(seal).not.toHaveClass('seal--rejected')
    expect(screen.queryByRole('button', { name: 'Approve and publish v2' })).not.toBeInTheDocument()
    // the audit entry of the new version is one click away (Brief, demo step 4)
    await user.click(screen.getByRole('button', { name: 'Open the rules' }))
    await user.click(screen.getByRole('button', { name: 'Open the audit log' }))
    expect(onOpenRules).toHaveBeenCalledOnce()
    expect(onOpenAudit).toHaveBeenCalledOnce()
  })

  it('rejects: the red seal and "Rejected. Nothing was published."', async () => {
    const { onPublished } = renderScreen()
    const user = await proposalShown()

    await user.click(screen.getByRole('button', { name: 'Reject' }))

    expect(await screen.findByText('Rejected. Nothing was published.')).toBeVisible()
    const seal = screen.getByText('Rejected', { selector: '.seal__kicker' }).closest('.seal')!
    expect(seal).toHaveClass('seal--rejected')
    expect(seal.querySelector('.seal__line')).toHaveTextContent('CR-0001 · 2026-09-27 09:13')
    expect(onPublished).not.toHaveBeenCalled()
  })

  it("says on the sandbox's own rule set that approving publishes the next version in it", async () => {
    const own = {
      ...rulesets.rulesets[0]!,
      protected: false,
      versions: [
        { versionNo: 1, status: 'SUPERSEDED' as const },
        { versionNo: 2, status: 'PUBLISHED' as const },
      ],
    }
    server.use(
      http.get(`${BASE}/rulesets`, () => HttpResponse.json({ rulesets: [own] })),
      http.get(`${BASE}/rulesets/:id/versions/:no`, () =>
        HttpResponse.json({ ...publishedVersion, protected: false, versionNo: 2 }),
      ),
    )
    renderScreen()
    await proposalShown()

    expect(screen.getByText('Published v2', { selector: '.reason .mono' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Approve and publish v3' })).toBeVisible()
    expect(screen.getByText('Approving publishes v3.')).toHaveClass('reason')
  })

  it('shows a refused approval as the refusal block, and leaves the proposal to be decided', async () => {
    server.use(
      http.post(`${BASE}/changes/:id/approve`, () =>
        HttpResponse.json(
          {
            code: 'VERSION_STATUS_CONFLICT',
            message: 'no longer the latest',
            details: [],
            traceId: 't',
          },
          { status: 409 },
        ),
      ),
    )
    const { onPublished } = renderScreen()
    const user = await proposalShown()

    await user.click(screen.getByRole('button', { name: 'Approve and publish v2' }))

    const refusal = await screen.findByRole('alert')
    expect(refusal).toHaveClass('refusal')
    expect(refusal.querySelector('.refusal__head')).toHaveTextContent(
      'VERSION_STATUS_CONFLICTThe approval was refused.',
    )
    // the spec's states table: "The version it was proposed on is no longer the latest…"
    expect(refusal.querySelector('.refusal__foot')).toHaveTextContent(
      /^The version it was proposed on is no longer the latest.*Nothing was stored\.$/,
    )
    expect(screen.getByRole('button', { name: 'Approve and publish v2' })).toBeEnabled()
    expect(onPublished).not.toHaveBeenCalled()
  })
})

describe('ChangeScreen, refusals', () => {
  it('RT-04: the refusal block, a row per refused patch, and what the model proposed behind a link', async () => {
    server.use(http.post(`${BASE}/rulesets/:id/versions/:no/changes`, () => streamed(rt04Events)))
    renderScreen()
    const user = await propose(`${TEXT}. ${RT_04_PLANTED}`)

    const refusal = await screen.findByRole('alert')
    expect(refusal).toHaveClass('refusal')
    expect(refusal.querySelector('.refusal__head')).toHaveTextContent(
      'RULESET_INVALIDThe proposal was refused.',
    )
    // Document 3, Patch validation: every remove the request does not name, and the defaults, are refused
    const pointers = within(refusal).getAllByRole('term')
    const problems = within(refusal).getAllByRole('definition')
    expect(pointers).toHaveLength(12)
    expect(pointers[0]).toHaveTextContent('/patches/2')
    expect(problems[0]).toHaveTextContent(
      'PATCH_REMOVES_UNMENTIONED · R-100 is removed, but the request names it neither by its id nor by a value its condition tests',
    )
    expect(problems[11]).toHaveTextContent('PATCH_SETS_DEFAULTS')
    expect(refusal.querySelector('.refusal__foot')).toHaveTextContent(
      'Nothing was stored. What the model proposed',
    )
    expect(screen.queryByRole('list', { name: 'What the model proposed' })).not.toBeInTheDocument()

    await user.click(within(refusal).getByRole('button', { name: 'What the model proposed' }))

    const attempted = within(
      screen.getByRole('list', { name: 'What the model proposed' }),
    ).getAllByRole('listitem')
    expect(attempted.map((item) => item.textContent)).toContain('Remove R-100')
    expect(attempted.at(-1)).toHaveTextContent('Set the defaults to Approved')
    expect(screen.queryByRole('button', { name: /^Approve and publish/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Patches' })).not.toBeInTheDocument()
  })

  it('says why when the API refuses the request before the stream opens', async () => {
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/changes`, () =>
        HttpResponse.json(
          {
            code: 'VERSION_STATUS_CONFLICT',
            message: 'not embedded yet',
            details: [],
            traceId: 't',
          },
          { status: 409 },
        ),
      ),
    )
    renderScreen()
    await propose()

    // Document 2: a 409 before any stream opens is a version that is not published, or not embedded yet
    const refusal = await screen.findByRole('alert')
    expect(refusal.querySelector('.refusal__head')).toHaveTextContent(
      'VERSION_STATUS_CONFLICTThe change could not be proposed.',
    )
    expect(refusal.querySelector('.refusal__foot')).toHaveTextContent(
      'Only a published version that has finished indexing takes a change. Try again in a moment. Nothing was stored.',
    )
  })

  it("says the day's model budget is spent when the model may not be called", async () => {
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/changes`, () =>
        streamed([
          ...scriptedEvents.slice(0, 2),
          [
            'error',
            {
              code: 'BUDGET_EXHAUSTED',
              findings: [],
              document: null,
              ended: { stage: 'proposing', ms: 3, tokens: null },
            },
          ],
        ]),
      ),
    )
    renderScreen()
    await propose()

    // Document 2: while the budget is spent a model call answers BUDGET_EXHAUSTED, and the scripted request is
    // still served from the cache
    const refusal = await screen.findByRole('alert')
    expect(refusal.querySelector('.refusal__head')).toHaveTextContent(
      'BUDGET_EXHAUSTEDThe change could not be proposed.',
    )
    expect(refusal.querySelector('.refusal__foot')).toHaveTextContent(
      "Today's model budget is spent; the demo's change request is still proposed from the cache, and any other waits until the budget resumes. Nothing was stored.",
    )
  })
})

describe('ChangeScreen.css', () => {
  it("carries every rule of the spec's change request, with the spec's declarations", () => {
    const change = specRules('.candidates {', '/* Diff, unified')

    expect(change).toHaveLength(6)
    expect(unported(stylesheet('features/change/ChangeScreen.css'), change)).toEqual([])
  })
})
