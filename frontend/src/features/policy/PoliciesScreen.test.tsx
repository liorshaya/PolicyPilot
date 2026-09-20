import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '../../test/msw/server'
import { lendingParagraphs } from '../../test/fixtures/lending'
import {
  SEEDED_RULESET_ID,
  SECOND_RULESET_ID,
  secondVersion,
  twoRulesets,
} from '../../test/msw/handlers'
import { PoliciesScreen } from './PoliciesScreen'

/**
 * The policy screen against realistic responses (Document 6, Frontend Test Design). The policy is the committed
 * Hebrew fixture, so the direction, the paragraph numbers and the text are the demo's own (NFR-5).
 */
function renderScreen(onOpenRules = () => undefined) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <PoliciesScreen onOpenRules={onOpenRules} />
    </QueryClientProvider>,
  )
}

describe('PoliciesScreen', () => {
  it('opens the seeded policy and numbers every paragraph', async () => {
    renderScreen()

    expect(await screen.findByText(lendingParagraphs[0]!.text)).toBeInTheDocument()
    expect(
      screen.getByText(lendingParagraphs[lendingParagraphs.length - 1]!.text),
    ).toBeInTheDocument()
    // the number beside a paragraph is what a rule cites (Document 3, Provenance)
    expect(screen.getByText(String(lendingParagraphs.length))).toBeInTheDocument()
  })

  it('reads a Hebrew policy right to left', async () => {
    renderScreen()

    const first = await screen.findByText(lendingParagraphs[0]!.text)
    const block = first.closest('[dir]')
    expect(block).toHaveAttribute('dir', 'rtl')
    expect(block).toHaveAttribute('lang', 'he')
  })

  it('lists the documents with their size and marks the seeded one', async () => {
    renderScreen()

    const item = await screen.findByRole('button', { name: /מדיניות אשראי צרכני/ })
    expect(within(item).getByText(`${lendingParagraphs.length} paragraphs`)).toBeInTheDocument()
    expect(within(item).getByText('Seeded')).toBeInTheDocument()
  })

  it('pastes a policy and sends the title, language and text', async () => {
    let sent: Record<string, unknown> | null = null
    server.use(
      http.post('http://localhost:8080/api/v1/policies', async ({ request }) => {
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

  it('says what was wrong when the API refuses the text', async () => {
    server.use(
      http.post('http://localhost:8080/api/v1/policies', () =>
        HttpResponse.json(
          {
            code: 'POLICY_INVALID',
            message: 'The policy is not valid.',
            details: [],
            traceId: 't',
          },
          { status: 422 },
        ),
      ),
    )
    renderScreen()

    await userEvent.click(await screen.findByRole('button', { name: 'Add policy' }))
    await userEvent.type(screen.getByLabelText('Title'), 'Too long')
    await userEvent.type(screen.getByLabelText('Policy text'), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Add policy' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('over its limits')
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

  it('cannot open rules for a policy that has none yet', async () => {
    server.use(
      // the policy was added but never generated from, so no rule set cites it; MSW takes the first match,
      // so this stands in front of the two-rule-set handlers
      http.get('http://localhost:8080/api/v1/rulesets', () => HttpResponse.json({ rulesets: [] })),
      ...twoRulesets(),
    )
    renderScreen()

    const open = await screen.findByRole('button', { name: 'Open its rules' })
    expect(open).toBeDisabled()
    expect(open).toHaveAttribute('title', 'Generate rules for this policy first')
  })

  it('reviewing a draft opens the rule set the generation just wrote', async () => {
    const onOpenRules = vi.fn()
    server.use(
      http.post('http://localhost:8080/api/v1/policies/:id/rulesets', () => {
        const body =
          'event:parsing\ndata:{}\n\nevent:authoring\ndata:{}\n\nevent:validating\ndata:{}\n\n' +
          `event:draft\ndata:${JSON.stringify(secondVersion)}\n\n`
        return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream' } })
      }),
    )
    renderScreen(onOpenRules)

    await userEvent.click(await screen.findByRole('button', { name: 'Generate rules' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Review the draft' }))

    expect(onOpenRules).toHaveBeenCalledWith(SECOND_RULESET_ID)
  })

  it('shows the error code when the list cannot be read', async () => {
    server.use(
      http.get('http://localhost:8080/api/v1/policies', () =>
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
