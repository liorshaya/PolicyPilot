import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import {
  approvedDecision,
  PROPOSAL_ID,
  rt04Events,
  scriptedEvents,
  scriptedRequest,
} from '../../test/fixtures/change'
import { eventStream, RT_04_PLANTED } from '../../test/fixtures/changeRequest'
import { SECOND_RULESET_ID, SEEDED_RULESET_ID, twoRulesets } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { ChangeScreen } from './ChangeScreen'

// @requirement FR-17
// @requirement FR-18
// @requirement FR-19

/**
 * The change screen (Brief, demo step 4: "The agent lists the two affected rules, shows a diff, reruns the 200 cases: 12
 * decisions flip, listed by id. Click Approve. Version 2 is published"; Work Plan day 14; Document 5, RT-04: "the stream
 * ends with the refusal and the model's answer, so the analyst sees what was attempted"). MSW answers with the streams
 * the API sends, built from the committed fixtures.
 */

const BASE = 'http://localhost:8080/api/v1'
const TEXT = scriptedRequest.text.he

const streamed = (events: [string, unknown][]) =>
  new HttpResponse(eventStream(events), { headers: { 'Content-Type': 'text/event-stream' } })

function renderScreen(rulesetId: string | null = SEEDED_RULESET_ID) {
  const onPublished = vi.fn()
  const onOpenAudit = vi.fn()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ChangeScreen rulesetId={rulesetId} onPublished={onPublished} onOpenAudit={onOpenAudit} />
    </QueryClientProvider>,
  )
  return { onPublished, onOpenAudit }
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
  await screen.findByRole('list', { name: 'Rules to change' })
  return user
}

describe('ChangeScreen', () => {
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

    expect(
      await screen.findByText(
        'The rule set on the workspace has no published version yet; the change is proposed on the seeded one.',
      ),
    ).toBeVisible()
    await proposalShown()

    expect(sent).toStrictEqual([`${SEEDED_RULESET_ID}/1`])
  })

  it('shows the four stages while the stream runs, and the rules considered once they arrive', async () => {
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
    const progress = await screen.findByRole('list', { name: 'Progress' })
    await waitFor(() =>
      expect(
        within(progress).getByText('Finding the rules the request touches').closest('li'),
      ).toHaveAttribute('aria-current', 'step'),
    )
    send(scriptedEvents.slice(1, 2))
    await waitFor(() =>
      expect(within(progress).getByText('Writing the patches').closest('li')).toHaveAttribute(
        'aria-current',
        'step',
      ),
    )
    // the Work Plan's five candidates of the scripted request, and the one field it touches
    expect(screen.getByText('R-170, R-410, R-020, R-200, R-320')).toBeVisible()
    expect(screen.getByText('monthly_income')).toBeVisible()

    send(scriptedEvents.slice(2))
    stream!.close()
    expect(await screen.findByRole('list', { name: 'Rules to change' })).toBeVisible()
  })

  it('lists the two affected rules with their rationale, and the three left unchanged', async () => {
    renderScreen()
    await proposalShown()

    const rules = within(screen.getByRole('list', { name: 'Rules to change' })).getAllByRole(
      'listitem',
    )
    expect(rules).toHaveLength(2)
    const [r170, r410] = scriptedRequest.expected.patches
    expect(rules[0]).toHaveTextContent('Replace R-170')
    expect(within(rules[0]!).getByText(r170!.rationale)).toHaveAttribute('dir', 'rtl')
    expect(rules[1]).toHaveTextContent('Replace R-410')
    expect(within(rules[1]!).getByText(r410!.rationale)).toBeVisible()
    expect(screen.getByText('Considered and left unchanged').nextElementSibling).toHaveTextContent(
      /^R-020, R-200, R-320$/,
    )
  })

  it('shows the diff of the proposal and its regression report, 12 flips listed by id', async () => {
    renderScreen()
    await proposalShown()

    const diff = screen.getByRole('region', { name: 'Changes from Version 1 to Proposed' })
    expect(within(diff).getByText('2 rules modified')).toBeVisible()
    const report = screen.getByRole('region', { name: 'Regression report' })
    expect(
      within(report).getByText('12 of the 200 decisions made on version 1 flip.'),
    ).toBeVisible()
    const [, body] = within(report).getAllByRole('rowgroup')
    expect(within(body!).getAllByRole('row')).toHaveLength(12)
  })

  it('approves with the note and says which version was published, and where', async () => {
    const sent: unknown[] = []
    server.use(
      http.post(`${BASE}/changes/:id/approve`, async ({ request, params }) => {
        sent.push({ id: params.id, body: await request.json() })
        return HttpResponse.json(approvedDecision)
      }),
    )
    const { onPublished, onOpenAudit } = renderScreen()
    const user = await proposalShown()

    await user.type(screen.getByLabelText('Note for the audit log'), 'אושר בוועדת האשראי')
    await user.click(screen.getByRole('button', { name: 'Approve and publish' }))

    expect(
      await screen.findByText(
        "Approved. Version 2 is published in this sandbox's own copy of the seeded rule set; version 1 is unchanged.",
      ),
    ).toBeVisible()
    expect(sent).toStrictEqual([{ id: PROPOSAL_ID, body: { note: 'אושר בוועדת האשראי' } }])
    expect(onPublished).toHaveBeenCalledWith(approvedDecision.result)
    expect(screen.queryByRole('button', { name: 'Approve and publish' })).not.toBeInTheDocument()
    // the audit entry of the new version is one click away (Brief, demo step 4)
    await user.click(screen.getByRole('button', { name: 'Open the audit log' }))
    expect(onOpenAudit).toHaveBeenCalledOnce()
  })

  it('rejects, and says that nothing was published', async () => {
    const { onPublished } = renderScreen()
    const user = await proposalShown()

    await user.click(screen.getByRole('button', { name: 'Reject' }))

    expect(await screen.findByText('Rejected. Nothing was published.')).toBeVisible()
    expect(onPublished).not.toHaveBeenCalled()
  })

  it('RT-04: shows each refusal and what the model attempted, stores nothing, and offers no approval', async () => {
    server.use(http.post(`${BASE}/rulesets/:id/versions/:no/changes`, () => streamed(rt04Events)))
    renderScreen()
    await propose(`${TEXT}. ${RT_04_PLANTED}`)

    const refusal = await screen.findByRole('alert')
    expect(refusal).toHaveTextContent(
      'The proposal was refused (RULESET_INVALID). Nothing was stored.',
    )
    const reasons = within(
      within(refusal).getByRole('list', { name: 'Why it was refused' }),
    ).getAllByRole('listitem')
    // Document 3, Patch validation: every remove the request does not name, and the defaults, are refused
    expect(reasons).toHaveLength(12)
    expect(reasons[0]).toHaveTextContent('PATCH_REMOVES_UNMENTIONED')
    expect(reasons[0]).toHaveTextContent('/patches/2')
    expect(reasons[11]).toHaveTextContent('PATCH_SETS_DEFAULTS')
    const attempted = within(
      within(refusal).getByRole('list', { name: 'What the model proposed' }),
    ).getAllByRole('listitem')
    expect(attempted.map((item) => item.textContent)).toContain('Remove R-100')
    expect(attempted.at(-1)).toHaveTextContent('Set the defaults to Approved')
    expect(screen.queryByRole('button', { name: 'Approve and publish' })).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Rules to change' })).not.toBeInTheDocument()
  })

  it('shows a refused approval, and leaves the proposal to be decided', async () => {
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

    await user.click(screen.getByRole('button', { name: 'Approve and publish' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The approval was refused (VERSION_STATUS_CONFLICT)',
    )
    expect(screen.getByRole('button', { name: 'Approve and publish' })).toBeEnabled()
    expect(onPublished).not.toHaveBeenCalled()
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
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Only a published version that has finished indexing takes a change. Try again in a moment.',
    )
  })

  it('writes the request and the note right to left when they are Hebrew, within the chat message limit', async () => {
    renderScreen()
    await proposalShown()

    for (const box of [
      screen.getByLabelText('What should change'),
      screen.getByLabelText('Note for the audit log'),
    ]) {
      expect(box).toHaveAttribute('dir', 'auto')
      expect(box).toHaveAttribute('maxlength', '1000')
    }
  })
})
