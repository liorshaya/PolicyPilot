import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { lendingParagraphs } from '../../test/fixtures/lending'
import { SEEDED_RULESET_ID } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { ChatScreen } from './ChatScreen'

/**
 * The assistant against the chat stream as the API sends it (Document 2: POST /chat/sessions, then token events,
 * citations, usage and done, or error). MSW answers with real event streams; the answers and citations are those the
 * API gives for the lending policy, whose paragraph 2 is the loan term and whose R-330 refers application 17.
 */

const BASE = 'http://localhost:8080/api/v1'
const SESSION = '0f4c1c9e-0000-4000-8000-0000000000d1'
const TERM_QUESTION = 'מהי תקופת ההחזר המקסימלית להלוואה?'

function streamOf(events: [string, unknown][], status = 200): HttpResponse<string> {
  const body = events
    .map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`)
    .join('')
  return new HttpResponse(body, { status, headers: { 'Content-Type': 'text/event-stream' } })
}

function answered(text: string, citations: unknown[]): [string, unknown][] {
  // a real stream arrives in pieces; the markers here are whole, as the API only lets whole markers through
  const pieces = text.match(/.{1,9}/gsu) ?? []
  return [
    ...pieces.map((piece): [string, unknown] => ['token', { text: piece }]),
    ['citations', { citations }],
    ['usage', { inputTokens: 900, outputTokens: 40, toolCalls: 0 }],
    ['done', { messageId: '0f4c1c9e-0000-4000-8000-0000000000e1' }],
  ]
}

function serveSession(language = 'he') {
  server.use(
    http.post(`${BASE}/chat/sessions`, () =>
      HttpResponse.json(
        { id: SESSION, rulesetId: SEEDED_RULESET_ID, versionNo: 1, language },
        { status: 201 },
      ),
    ),
  )
}

function renderScreen(onOpenRule = vi.fn(), onOpenCases = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ChatScreen onOpenRule={onOpenRule} onOpenCases={onOpenCases} />
    </QueryClientProvider>,
  )
  return { onOpenRule, onOpenCases }
}

async function ask(question: string) {
  const user = userEvent.setup()
  const input = await screen.findByLabelText('Question')
  await waitFor(() => expect(screen.getByRole('button', { name: 'Ask' })).toBeDisabled())
  await user.type(input, question)
  await waitFor(() => expect(screen.getByRole('button', { name: 'Ask' })).toBeEnabled())
  await user.click(screen.getByRole('button', { name: 'Ask' }))
  return user
}

describe('ChatScreen', () => {
  it('streams an answer in the policy language, with its citation as a chip', async () => {
    serveSession()
    server.use(
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, () =>
        streamOf(
          answered('תקופת ההחזר היא עד 84 חודשים.[[p:2]]', [
            { id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 },
          ]),
        ),
      ),
    )
    renderScreen()

    await ask(TERM_QUESTION)

    const answer = await screen.findByText('תקופת ההחזר היא עד 84 חודשים.')
    const block = answer.closest('p')!
    expect(block).toHaveAttribute('dir', 'rtl')
    expect(block).toHaveAttribute('lang', 'he')
    expect(within(block).getByRole('button', { name: '¶ 2' })).toBeInTheDocument()
  })

  it('opens the cited paragraph beside the conversation', async () => {
    serveSession()
    server.use(
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, () =>
        streamOf(answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }])),
      ),
    )
    renderScreen()
    const user = await ask(TERM_QUESTION)

    await user.click(await screen.findByRole('button', { name: '¶ 2' }))

    const source = screen.getByRole('complementary', { name: 'Paragraph 2' })
    expect(within(source).getByText(lendingParagraphs[1]!.text)).toBeInTheDocument()
  })

  it('opens the cited rule with its own id, and a decision in the cases', async () => {
    serveSession()
    server.use(
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, () =>
        streamOf(
          answered('Referred.[[d:17]] The rule asks for a guarantor.[[r:R-330]]', [
            { id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer' },
            { id: 'r:R-330', kind: 'RULE', ruleId: 'R-330', paragraph: 7, label: 'בדיקת חתם' },
          ]),
        ),
      ),
    )
    const { onOpenRule, onOpenCases } = renderScreen()
    const user = await ask('למה בקשה מספר 17 הופנתה לבדיקה?')

    await user.click(await screen.findByRole('button', { name: 'R-330' }))
    await user.click(screen.getByRole('button', { name: 'Application 17' }))

    expect(onOpenRule).toHaveBeenCalledWith('R-330')
    expect(onOpenCases).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Application 17' })).toHaveAttribute(
      'title',
      'Application 17 · refer, as the engine decided',
    )
  })

  it('shows a simulation as its change and outcome, and hides a marker the API did not cite', async () => {
    serveSession()
    server.use(
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, () =>
        streamOf(
          answered('Approved.[[sim:d17:has_guarantor=true]][[p:9]]', [
            {
              id: 'sim:d17:has_guarantor=true',
              kind: 'SIMULATION',
              applicationNumber: 17,
              outcome: 'approve',
              detail: 'has_guarantor=true',
            },
          ]),
        ),
      ),
    )
    renderScreen()
    await ask('האם בקשה 17 הייתה מאושרת אם היה ערב?')

    const chip = await screen.findByText('What if has_guarantor=true')
    expect(chip).toHaveAttribute('title', 'If has_guarantor=true: approve, as the engine simulated')
    expect(screen.queryByText('¶ 9')).not.toBeInTheDocument()
  })

  it('says why an answer failed and asks it again on request', async () => {
    serveSession()
    let calls = 0
    server.use(
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, () => {
        calls++
        return calls === 1
          ? streamOf([['error', { code: 'PROVIDER_UNAVAILABLE' }]])
          : streamOf(
              answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]),
            )
      }),
    )
    renderScreen()
    const user = await ask(TERM_QUESTION)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The model did not answer in time. Try again.',
    )
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('84 months.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(calls).toBe(2)
  })

  it('names a version that is not indexed yet', async () => {
    serveSession()
    server.use(
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, () =>
        HttpResponse.json(
          { code: 'VERSION_STATUS_CONFLICT', message: 'not ready', details: [], traceId: 't' },
          { status: 409 },
        ),
      ),
    )
    renderScreen()
    await ask(TERM_QUESTION)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This version is still being indexed for questions. Try again in a moment.',
    )
  })

  it('offers nothing to ask when no version is published', async () => {
    server.use(
      http.get(`${BASE}/rulesets`, () =>
        HttpResponse.json({
          rulesets: [
            {
              id: SEEDED_RULESET_ID,
              name: 'Draft',
              domain: 'd',
              protected: false,
              versions: [{ versionNo: 1, status: 'DRAFT' }],
            },
          ],
        }),
      ),
    )
    renderScreen()

    expect(await screen.findByText('No published version to ask about')).toBeInTheDocument()
  })
})
