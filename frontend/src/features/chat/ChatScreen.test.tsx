import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { budgetSpent } from '../../test/fixtures/budget'
import { notCovered } from '../../test/fixtures/english'
import { lendingParagraphs } from '../../test/fixtures/lending'
import { SEEDED_RULESET_ID } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { specRules, stylesheet, unported } from '../../test/css'
import { rtlSnapshot } from '../../test/rtlSnapshot'
import { SCRIPTED_QUESTIONS } from '../demo/steps'
import { ChatScreen } from './ChatScreen'
import type { ChatToolCall } from './types'

// @requirement FR-13
// @requirement NFR-5

/**
 * The assistant against the chat stream as the API sends it (Document 2: POST /chat/sessions, then a tool event for
 * each tool call, token events, citations, usage and done with the fixed sentence the answer is, or error), drawn as
 * the Register's paper trail (the spec, section 09, "The assistant"): a right-to-left thread inside the English chrome,
 * the marks on the reading-start side, each tool call a step line above its answer, citations after the punctuation
 * and repeated in the sources strip. MSW answers with real event streams; the answers and citations are those the API
 * gives for the lending policy, whose paragraph 2 is the loan term and whose R-330 refers application 17. The engine's
 * values in the tool events are the Python reference's.
 */

const BASE = 'http://localhost:8080/api/v1'
const SESSION = '0f4c1c9e-0000-4000-8000-0000000000d1'
const TERM_QUESTION = 'מהי תקופת ההחזר המקסימלית להלוואה?'

/** Application 17 as getDecision reads it: referred by R-330, no flag (Document 3's worked example). */
const lookup: ChatToolCall = {
  tool: 'getDecision',
  applicationNumber: 17,
  overrides: null,
  tag: null,
  versionNo: 1,
  micros: 1240,
  outcome: 'refer',
  decidingRuleId: 'R-330',
  flags: [],
  refused: null,
}

/** Application 17 with a guarantor, as simulate decides it: approved by R-900, flagged by R-420. */
const whatIf: ChatToolCall = {
  ...lookup,
  tool: 'simulate',
  overrides: 'has_guarantor=true',
  micros: 58,
  outcome: 'approve',
  decidingRuleId: 'R-900',
  flags: ['STABLE_INCOME_MANUAL_CHECK'],
}

function streamOf(events: [string, unknown][], status = 200): HttpResponse<string> {
  const body = events
    .map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`)
    .join('')
  return new HttpResponse(body, { status, headers: { 'Content-Type': 'text/event-stream' } })
}

function answered(
  text: string,
  citations: unknown[],
  { steps = [], fixed = null }: { steps?: ChatToolCall[]; fixed?: string | null } = {},
): [string, unknown][] {
  // a real stream arrives in pieces; the markers here are whole, as the API only lets whole markers through
  const pieces = text.match(/.{1,9}/gsu) ?? []
  return [
    ...steps.map((step): [string, unknown] => ['tool', step]),
    ...pieces.map((piece): [string, unknown] => ['token', { text: piece }]),
    ['citations', { citations }],
    ['usage', { inputTokens: 900, outputTokens: 40, toolCalls: steps.length }],
    ['done', { messageId: '0f4c1c9e-0000-4000-8000-0000000000e1', fixed }],
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

function serveAnswer(events: [string, unknown][]) {
  server.use(http.post(`${BASE}/chat/sessions/${SESSION}/messages`, () => streamOf(events)))
}

function renderScreen(
  onOpenRule = vi.fn(),
  onOpenCases = vi.fn(),
  rulesetId: string | null = null,
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ChatScreen onOpenRule={onOpenRule} onOpenCases={onOpenCases} rulesetId={rulesetId} />
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

/** The thread, and in it the turns: a question and its answer each. */
function thread(): HTMLElement {
  return screen.getByRole('log', { name: 'Conversation' })
}

function turns(): HTMLElement[] {
  return [...thread().querySelectorAll<HTMLElement>('.turn')]
}

/** The answer turn of the last question, once it has arrived. */
async function lastAnswer(): Promise<HTMLElement> {
  await waitFor(() => expect(turns().at(-1)).toHaveClass('turn--a'))
  return turns().at(-1)!
}

describe('ChatScreen · the thread', () => {
  it('is a right-to-left container inside the English chrome, the marks on the reading-start side', async () => {
    serveSession()
    serveAnswer(answered('תקופת ההחזר היא עד 84 חודשים.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]))
    renderScreen()

    await ask(TERM_QUESTION)

    const answer = await lastAnswer()
    expect(thread()).toHaveAttribute('dir', 'rtl')
    expect(thread()).toHaveAttribute('lang', 'he')
    expect(screen.getByRole('heading', { level: 1, name: 'Assistant' }).closest('[dir="rtl"]')).toBeNull()
    const [question] = turns()
    // in a right-to-left container the first child stands on the right, where a Hebrew reader starts
    expect(question!.firstElementChild).toHaveClass('turn__who')
    expect(question!.firstElementChild!.querySelector('.actor--person')).not.toBeNull()
    expect(answer.firstElementChild).toHaveClass('turn__who')
    expect(answer.firstElementChild!.querySelector('.actor--model')).not.toBeNull()
  })

  it('writes a question as a Hebrew block and an answer as a document block, each number isolated', async () => {
    serveSession()
    serveAnswer(answered('תקופת ההחזר היא עד 84 חודשים.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]))
    renderScreen()

    await ask(TERM_QUESTION)

    const answer = (await lastAnswer()).querySelector('p.answer')!
    const question = turns()[0]!.querySelector('.turn__body')!
    expect(question).toHaveAttribute('lang', 'he')
    expect(question).toHaveAttribute('dir', 'rtl')
    expect(question).toHaveTextContent(TERM_QUESTION)
    expect(answer).toHaveAttribute('lang', 'he')
    expect(answer).toHaveAttribute('dir', 'rtl')
    expect(within(answer as HTMLElement).getByText('84')).toHaveAttribute('dir', 'ltr')
  })

  it('isolates the numbers of a Hebrew question: −12 and 8,000 ₪, the hard cases of section 03', async () => {
    serveSession()
    serveAnswer(answered('ההפרש הוא −12.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]))
    renderScreen()

    await ask('מה קורה כשההפרש הוא −12 וההכנסה 8,000 ₪?')

    const question = turns()[0]!.querySelector('.turn__body')!
    const isolates = [...question.querySelectorAll('bdi')].map((bdi) => [bdi.textContent, bdi.getAttribute('dir')])
    expect(isolates).toEqual([
      [`${String.fromCodePoint(0x2212)}12`, 'ltr'],
      [`8,000${String.fromCodePoint(0x00a0)}₪`, 'ltr'],
    ])
  })

  it('puts a citation chip after the sentence’s punctuation, one per claim, and the sources strip repeats them all', async () => {
    serveSession()
    serveAnswer(
      answered('בקשה 17 הופנתה לבדיקת חתם[[d:17]][[p:7]].', [
        { id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer', ruleId: 'R-330' },
        { id: 'p:7', kind: 'PARAGRAPH', paragraph: 7 },
      ]),
    )
    renderScreen()

    await ask('למה בקשה מספר 17 הופנתה לבדיקה?')

    const answer = (await lastAnswer()).querySelector<HTMLElement>('p.answer')!
    const inline = await within(answer).findByRole('button', { name: 'Case 17' })
    expect(inline.previousSibling?.textContent).toMatch(/\.$/)
    expect(within(answer).queryByRole('button', { name: 'Paragraph 7' })).not.toBeInTheDocument()
    const sources = within(await lastAnswer()).getByRole('group', { name: 'Sources' })
    expect(within(sources).getByText('Cited')).toBeInTheDocument()
    expect(within(sources).getByRole('button', { name: 'Case 17' })).toBeInTheDocument()
    expect(within(sources).getByRole('button', { name: 'Paragraph 7' })).toHaveTextContent('¶ 7')
    expect(sources).toHaveTextContent("the outcome is the engine's, on v1")
  })

  it('opens the cited paragraph in the margin while the thread stays put', async () => {
    serveSession()
    serveAnswer(answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]))
    renderScreen()
    const user = await ask(TERM_QUESTION)

    const [inline] = await within(await lastAnswer()).findAllByRole('button', { name: 'Paragraph 2' })
    await user.click(inline!)

    const source = screen.getByRole('complementary', { name: 'Paragraph 2' })
    expect(within(source).getByText(lendingParagraphs[1]!.text)).toBeInTheDocument()
    expect(thread()).toBeInTheDocument()
  })

  it('opens a cited rule by its own id, and the decision chip reads Case 17 and opens the cases', async () => {
    serveSession()
    serveAnswer(
      answered('Referred.[[d:17]] The rule asks for a guarantor.[[r:R-330]]', [
        { id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer' },
        { id: 'r:R-330', kind: 'RULE', ruleId: 'R-330', paragraph: 7, label: 'בדיקת חתם' },
      ]),
    )
    const { onOpenRule, onOpenCases } = renderScreen()
    const user = await ask('למה בקשה מספר 17 הופנתה לבדיקה?')
    const answer = (await lastAnswer()).querySelector<HTMLElement>('p.answer')!

    await user.click(await within(answer).findByRole('button', { name: 'R-330' }))
    await user.click(within(answer).getByRole('button', { name: 'Case 17' }))

    expect(onOpenRule).toHaveBeenCalledWith('R-330')
    expect(onOpenCases).toHaveBeenCalledOnce()
    expect(within(answer).getByRole('button', { name: 'Case 17' })).toHaveAttribute(
      'title',
      'Case 17 · Manual review, as the engine decided',
    )
  })

  it('shows a simulation as the tool chip with its change and outcome, and hides a marker the API did not cite', async () => {
    serveSession()
    serveAnswer(
      answered('Approved.[[sim:d17:has_guarantor=true]] Also.[[p:9]]', [
        {
          id: 'sim:d17:has_guarantor=true',
          kind: 'SIMULATION',
          applicationNumber: 17,
          outcome: 'approve',
          detail: 'has_guarantor=true',
        },
      ]),
    )
    renderScreen()
    await ask('האם בקשה 17 הייתה מאושרת אם היה ערב?')

    const answer = (await lastAnswer()).querySelector<HTMLElement>('p.answer')!
    const chip = await within(answer).findByText('what-if')
    expect(chip.closest('.chip')).toHaveClass('chip--tool')
    expect(chip.closest('.chip')).toHaveAttribute(
      'title',
      'What if has_guarantor=true: Approved, as the engine simulated',
    )
    expect(screen.queryByText('¶ 9')).not.toBeInTheDocument()
  })
})

describe('ChatScreen · tool calls', () => {
  it('draws each tool call as a step line above its answer: the tool chip, what it ran on, the time and the outcome', async () => {
    serveSession()
    serveAnswer(
      answered('כן, עם ערב הבקשה הייתה מאושרת.[[r:R-900]]', [{ id: 'r:R-900', kind: 'RULE', ruleId: 'R-900' }], {
        steps: [whatIf],
      }),
    )
    renderScreen()

    await ask('האם בקשה 17 הייתה מאושרת אם היה ערב?')

    const answer = await lastAnswer()
    const steps = within(answer).getByRole('list', { name: 'Tool calls' })
    const [step] = within(steps).getAllByRole('listitem')
    expect(step!.querySelector('.chip--tool')).toHaveTextContent('what-if · case 17 · has_guarantor=true')
    expect(step).toHaveTextContent('ran on v1 · 58 µs')
    expect(within(step!).getByText('Approved')).toHaveClass('tag--approve')
    expect(step).toHaveTextContent('flag STABLE_INCOME_MANUAL_CHECK')
    // the step line stands above the answer that used it
    const body = answer.querySelector('.turn__body')!
    expect([...body.children].indexOf(steps)).toBeLessThan([...body.children].indexOf(body.querySelector('p.answer')!))
  })

  it('names a case it looked up, the decision it read, and a call it refused', async () => {
    serveSession()
    serveAnswer(
      answered('בקשה 17 הופנתה לבדיקה.[[d:17]]', [{ id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer' }], {
        steps: [lookup, { ...lookup, applicationNumber: 999, micros: 310, outcome: null, decidingRuleId: null, refused: 'not_found' }],
      }),
    )
    renderScreen()

    await ask('למה בקשה מספר 17 הופנתה לבדיקה?')

    const [read, refused] = within(within(await lastAnswer()).getByRole('list', { name: 'Tool calls' })).getAllByRole('listitem')
    expect(read!.querySelector('.chip--tool')).toHaveTextContent('case 17')
    expect(read).toHaveTextContent('ran on v1 · 1.2 ms')
    expect(within(read!).getByText('Manual review')).toHaveClass('tag--refer')
    expect(refused!.querySelector('.chip--tool')).toHaveTextContent('case 999')
    expect(refused).toHaveTextContent('not in this session')
    expect(refused!.querySelector('.tag')).toBeNull()
  })
})

describe('ChatScreen · the system speaks', () => {
  it('marks the fixed not-covered sentence as the system’s, with no source', async () => {
    serveSession()
    serveAnswer(answered(notCovered('he'), [], { fixed: 'not_covered' }))
    renderScreen()

    await ask('מהי הריבית המקסימלית שהבנק רשאי לגבות?')

    const answer = await lastAnswer()
    expect(answer.querySelector('.turn__who .actor--system')).not.toBeNull()
    expect(answer.querySelector('.turn__who .actor--model')).toBeNull()
    expect(answer.querySelector('p.answer')).toHaveClass('answer--fixed')
    expect(within(answer).getByText("No source · a fixed sentence, not the model's")).toBeInTheDocument()
  })

  it('marks the tool-limit sentence as the system’s too', async () => {
    serveSession()
    serveAnswer(answered('השאלה דורשת יותר בדיקות ממה שתשובה אחת רשאית לבצע.', [], { steps: [lookup], fixed: 'tool_limit' }))
    renderScreen()

    await ask('למה בקשה מספר 17 הופנתה לבדיקה?')

    const answer = await lastAnswer()
    expect(answer.querySelector('.turn__who .actor--system')).not.toBeNull()
    expect(within(answer).getByText("No source · a fixed sentence, not the model's")).toBeInTheDocument()
  })

  it('shows a withheld answer as the amber note with the system’s mark', async () => {
    serveSession()
    serveAnswer([['error', { code: 'ANSWER_WITHHELD' }]])
    renderScreen()

    await ask(TERM_QUESTION)

    const answer = await lastAnswer()
    expect(answer.querySelector('.turn__who .actor--system')).not.toBeNull()
    const note = answer.querySelector('.note--warning')!
    expect(note).toHaveTextContent('The answer was withheld because it contained something that must not be shown.')
  })

  it('shows the caret while an answer streams', async () => {
    serveSession()
    server.use(
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, ({ request }) => {
        const encoder = new TextEncoder()
        const body = new ReadableStream<Uint8Array>({
          start(controller) {
            controller.enqueue(encoder.encode(`event:token\ndata:${JSON.stringify({ text: 'תקופת ההחזר' })}\n\n`))
            // the answer is still coming; the stream ends only when the screen goes away
            request.signal.addEventListener('abort', () => controller.error(new DOMException('aborted', 'AbortError')))
          },
        })
        return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream' } })
      }),
    )
    renderScreen()

    await ask(TERM_QUESTION)

    const answer = await within(await lastAnswer()).findByText('תקופת ההחזר')
    expect(answer.closest('p')).toHaveClass('streaming')
  })

  it('takes the caret away once the answer is done', async () => {
    serveSession()
    serveAnswer(answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]))
    renderScreen()

    await ask(TERM_QUESTION)

    const answer = (await lastAnswer()).querySelector('p.answer')!
    await waitFor(() => expect(answer).not.toHaveClass('streaming'))
    expect(answer).toHaveTextContent('84 months.')
  })
})

describe('ChatScreen · the notes under the composer', () => {
  it.each([
    ['RATE_LIMITED', 'Too many questions in a short time. Wait a minute, then try again.'],
    ['PROVIDER_UNAVAILABLE', 'The model did not answer in time. Try again.'],
  ])('says %s in a note under the composer, and the composer stays open', async (code, text) => {
    serveSession()
    serveAnswer([['error', { code }]])
    renderScreen()

    await ask(TERM_QUESTION)

    const note = await screen.findByRole('alert')
    expect(note).toHaveTextContent(text)
    expect(note.querySelector('.actor--system')).not.toBeNull()
    expect(screen.getByLabelText('Question')).toBeEnabled()
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

  // the owner's answer of 2026-09-28 (the board, phase 4, question 9): the composer stays open while the budget is
  // spent, because the scripted questions are still answered from the cache
  it('says the day’s budget is spent and when it resumes, and the composer stays open', async () => {
    serveSession()
    server.use(http.get(`${BASE}/system/budget`, () => HttpResponse.json(budgetSpent)))
    renderScreen()

    const note = await screen.findByRole('note', { name: 'Budget' })

    expect(note).toHaveTextContent(
      "Today's model budget is spent until 00:00. The demo's questions are still answered from the cache.",
    )
    expect(screen.getByLabelText('Question')).toBeEnabled()
  })

  it('reads the budget again when a question finds it spent', async () => {
    serveSession()
    let spent = false
    server.use(
      http.get(`${BASE}/system/budget`, () =>
        HttpResponse.json({ ...budgetSpent, spent }),
      ),
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, () => {
        spent = true
        return streamOf([['error', { code: 'BUDGET_EXHAUSTED' }]])
      }),
    )
    renderScreen()

    await ask(TERM_QUESTION)

    expect(await screen.findByRole('note', { name: 'Budget' })).toHaveTextContent(
      "Today's model budget is spent until 00:00.",
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('asks a failed question again on request', async () => {
    serveSession()
    let calls = 0
    server.use(
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, () => {
        calls++
        return calls === 1
          ? streamOf([['error', { code: 'PROVIDER_UNAVAILABLE' }]])
          : streamOf(answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]))
      }),
    )
    renderScreen()
    const user = await ask(TERM_QUESTION)

    await user.click(within(await screen.findByRole('alert')).getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect((turns().at(-1)!).querySelector('p.answer')).toHaveTextContent('84 months.'))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(calls).toBe(2)
  })
})

describe('ChatScreen · before and around the conversation', () => {
  it('offers the three scripted questions as quiet buttons that fill the composer', async () => {
    serveSession()
    renderScreen()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: SCRIPTED_QUESTIONS[1] }))

    expect(screen.getByLabelText('Question')).toHaveValue(SCRIPTED_QUESTIONS[1])
    for (const question of SCRIPTED_QUESTIONS) {
      expect(screen.getByRole('button', { name: question })).toHaveClass('btn--quiet')
    }
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

  // Document 2, chat sessions: a session is opened on a PUBLISHED version. After demo step 1 the workspace is on the
  // draft just written, and the questions are asked of the seeded published version instead, which the screen says
  it('asks about the seeded published version when the workspace is on a draft', async () => {
    const DRAFT_ID = '0f4c1c9e-0000-4000-8000-0000000000b9'
    const opened: unknown[] = []
    server.use(
      http.get(`${BASE}/rulesets`, () =>
        HttpResponse.json({
          rulesets: [
            {
              id: SEEDED_RULESET_ID,
              name: 'מדיניות אשראי צרכני',
              domain: 'consumer-lending',
              protected: true,
              policyId: '0f4c1c9e-0000-4000-8000-0000000000a1',
              versions: [{ versionNo: 1, status: 'PUBLISHED' }],
            },
            {
              id: DRAFT_ID,
              name: 'מדיניות אשראי צרכני',
              domain: 'consumer-lending',
              protected: false,
              policyId: '0f4c1c9e-0000-4000-8000-0000000000a1',
              versions: [{ versionNo: 1, status: 'DRAFT' }],
            },
          ],
        }),
      ),
      http.post(`${BASE}/chat/sessions`, async ({ request }) => {
        opened.push(await request.json())
        return HttpResponse.json(
          { id: SESSION, rulesetId: SEEDED_RULESET_ID, versionNo: 1, language: 'he' },
          { status: 201 },
        )
      }),
    )

    renderScreen(vi.fn(), vi.fn(), DRAFT_ID)

    expect(
      await screen.findByText(
        'The rule set on the workspace has no published version yet; the questions are about the seeded one.',
      ),
    ).toBeInTheDocument()
    expect(await screen.findByLabelText('Question')).toBeInTheDocument()
    await waitFor(() => expect(opened).toEqual([{ rulesetId: SEEDED_RULESET_ID, versionNo: 1 }]))
  })
})

describe('ChatScreen in both directions (NFR-5)', () => {
  // two of the labeled set's English questions (fixtures/eval/questions.json, Q-13 and Q-15), asked here of an English
  // version, whose answers and not-covered sentence are English
  const MINIMUM_AGE = 'What is the minimum age for a personal loan?'
  const APPROVAL_DAYS = 'How many days does the bank take to approve a loan?'

  it('RTL: a Hebrew thread reads right to left, its steps, chips and numbers left to right (snapshot)', async () => {
    serveSession()
    serveAnswer(
      answered(
        'בקשה 17 הופנתה לבדיקת חתם.[[d:17]][[p:7]]',
        [
          { id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer', ruleId: 'R-330' },
          { id: 'p:7', kind: 'PARAGRAPH', paragraph: 7 },
        ],
        { steps: [lookup] },
      ),
    )
    renderScreen()

    await ask('למה בקשה מספר 17 הופנתה לבדיקה?')

    const answer = await lastAnswer()
    expect(await within(answer).findByRole('group', { name: 'Sources' })).toBeInTheDocument()
    expect(rtlSnapshot(thread())).toMatchSnapshot()
  })

  it('LTR: an English thread stays left to right (snapshot)', async () => {
    serveSession('en')
    serveAnswer(answered('The minimum age is 21.[[p:1]]', [{ id: 'p:1', kind: 'PARAGRAPH', paragraph: 1 }]))
    renderScreen()

    await ask(MINIMUM_AGE)

    const answer = await screen.findByText('The minimum age is 21.')
    expect(answer.closest('p')).toHaveAttribute('dir', 'ltr')
    expect(thread()).toHaveAttribute('dir', 'ltr')
    expect(rtlSnapshot(thread())).toMatchSnapshot()
  })

  it("shows the not-covered sentence in the version's language, Hebrew right to left and English left to right", async () => {
    for (const [language, question] of [
      ['he', TERM_QUESTION],
      ['en', APPROVAL_DAYS],
    ] as const) {
      serveSession(language)
      serveAnswer(answered(notCovered(language), [], { fixed: 'not_covered' }))
      renderScreen()

      await ask(question)

      const sentence = await screen.findByText(notCovered(language))
      expect(sentence.closest('p')).toHaveAttribute('dir', language === 'he' ? 'rtl' : 'ltr')
      expect(sentence.closest('p')).toHaveAttribute('lang', language)
      expect(within(sentence.closest('p')!).queryByRole('button')).not.toBeInTheDocument()
      cleanup()
    }
  })
})

describe('ChatScreen.css', () => {
  it("carries every rule of the spec's assistant thread, with the spec's declarations", () => {
    // the keyframes' own step is carried with the keyframes, which the stylesheet's top level leaves out
    const thread = specRules('/* The assistant thread', '/* Change request */').filter(
      ([selector]) => selector !== 'to',
    )

    expect(thread).toHaveLength(14)
    expect(unported(stylesheet('features/chat/ChatScreen.css'), thread)).toEqual([])
    expect(stylesheet('features/chat/ChatScreen.css')).toContain('@keyframes blink')
  })
})
