import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { keys } from '../../api/queries'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { budgetSpent } from '../../test/fixtures/budget'
import { notCovered } from '../../test/fixtures/english'
import { lendingParagraphs, lendingRuleSet } from '../../test/fixtures/lending'
import { PERSON } from '../../shared/ui/Actor'
import { batch, decision, SEEDED_RULESET_ID } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { rule, specRules, stylesheet, unported } from '../../test/css'
import { rtlSnapshot } from '../../test/rtlSnapshot'
import { windowAt } from '../../test/viewport'
import type { ChatConversationResponse, ChatSessionSummary } from '../../api/types'
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

/** Case 17 as getDecision reads it: referred by R-330, no flag (Document 3's worked example). */
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

/** Case 17 with a guarantor, as simulate decides it: approved by R-900, flagged by R-420. */
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
    serveAnswer(
      answered('תקופת ההחזר היא עד 84 חודשים.[[p:2]]', [
        { id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 },
      ]),
    )
    renderScreen()

    await ask(TERM_QUESTION)

    const answer = await lastAnswer()
    expect(thread()).toHaveAttribute('dir', 'rtl')
    expect(thread()).toHaveAttribute('lang', 'he')
    expect(
      screen.getByRole('heading', { level: 1, name: 'Assistant' }).closest('[dir="rtl"]'),
    ).toBeNull()
    const [question] = turns()
    // in a right-to-left container the first child stands on the right, where a Hebrew reader starts
    expect(question!.firstElementChild).toHaveClass('turn__who')
    expect(question!.firstElementChild!.querySelector('.actor--person')).not.toBeNull()
    expect(answer.firstElementChild).toHaveClass('turn__who')
    expect(answer.firstElementChild!.querySelector('.actor--model')).not.toBeNull()
  })

  // the spec, section 09 (v3.3): the word beside the mark, and what the answer did, on a head line above each turn
  it('names who speaks on a head line above each turn: the analyst, the model with its tool calls, the system', async () => {
    serveSession()
    serveAnswer(
      answered(
        'בקשה 17 הופנתה לבדיקה.[[d:17]]',
        [{ id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer' }],
        { steps: [lookup, whatIf] },
      ),
    )
    renderScreen()

    await ask('למה בקשה מספר 17 הופנתה לבדיקה?')

    const answer = await lastAnswer()
    const [question] = turns()
    const heads = (turn: HTMLElement) =>
      [...turn.querySelector('.turn__head')!.children].map((segment) => segment.textContent)
    expect(heads(question!)).toEqual([PERSON])
    expect(heads(answer)).toEqual(['Model', '2 tool calls'])
    // a count leads the phrase, which the bidi law isolates left to right inside the Hebrew thread (section 03)
    expect(answer.querySelector('.turn__head bdi')).toHaveAttribute('dir', 'ltr')
    // the head stands between the mark and the body, so the mark keeps the reading-start side
    expect(question!.children[1]).toHaveClass('turn__head')
    expect(answer.children[1]).toHaveClass('turn__head')
    expect(answer.children[2]).toHaveClass('turn__body')
  })

  it('names one tool call in the singular, and none at all when the answer made none', async () => {
    serveSession()
    serveAnswer(
      answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }], {
        steps: [lookup],
      }),
    )
    renderScreen()

    await ask(TERM_QUESTION)

    const answer = await lastAnswer()
    expect(answer.querySelector('.turn__head')).toHaveTextContent(/^Model1 tool call$/)
    cleanup()

    serveSession()
    serveAnswer(answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]))
    renderScreen()

    await ask(TERM_QUESTION)

    expect((await lastAnswer()).querySelector('.turn__head')).toHaveTextContent(/^Model$/)
  })

  it('writes a question as a Hebrew block and an answer as a document block, each number isolated', async () => {
    serveSession()
    serveAnswer(
      answered('תקופת ההחזר היא עד 84 חודשים.[[p:2]]', [
        { id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 },
      ]),
    )
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
    const isolates = [...question.querySelectorAll('bdi')].map((bdi) => [
      bdi.textContent,
      bdi.getAttribute('dir'),
    ])
    expect(isolates).toEqual([
      [`${String.fromCodePoint(0x2212)}12`, 'ltr'],
      [`8,000${String.fromCodePoint(0x00a0)}₪`, 'ltr'],
    ])
  })

  it("puts a citation chip after the sentence's punctuation, one per claim, and the sources strip repeats them all", async () => {
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

    const [inline] = await within(await lastAnswer()).findAllByRole('button', {
      name: 'Paragraph 2',
    })
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

  // the spec, section 08: a chip opens its target, and the target flashes once; the case is the one this session ran
  it("opens the trace of the cited case when this session has run the cases on the answer's version", async () => {
    serveSession()
    serveAnswer(
      answered('Referred.[[d:17]]', [
        { id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer' },
      ]),
    )
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(keys.run(SEEDED_RULESET_ID, 1), batch)
    const onOpenCase = vi.fn()
    const onOpenCases = vi.fn()
    render(
      <QueryClientProvider client={client}>
        <ChatScreen
          onOpenRule={vi.fn()}
          onOpenCases={onOpenCases}
          onOpenCase={onOpenCase}
          rulesetId={null}
        />
      </QueryClientProvider>,
    )
    const user = await ask('למה בקשה מספר 17 הופנתה לבדיקה?')
    const answer = (await lastAnswer()).querySelector<HTMLElement>('p.answer')!

    await user.click(await within(answer).findByRole('button', { name: 'Case 17' }))

    expect(onOpenCase).toHaveBeenCalledWith(decision.id)
    expect(onOpenCases).not.toHaveBeenCalled()
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

/** The Assistant row of the states matrix (the spec, section 11) that no other test covers. */
describe('ChatScreen · the header', () => {
  // the spec's provenance lines (section 04) lead with the rule set's id in mono, and section 12 puts no English label in a
  // row with a Hebrew value: the rail names the policy in Hebrew, at its own size
  it("leads the provenance with the rule set's id, with no Hebrew in the line", async () => {
    renderScreen()

    await waitFor(() =>
      expect(document.querySelector('.ws-header .prov')).toHaveTextContent('consumer-lending'),
    )
    const line = document.querySelector('.ws-header .prov')!
    expect([...line.children].map((segment) => segment.textContent)).toStrictEqual([
      'consumer-lending',
      'answers cite the policy and the rules; the engine decided every outcome they report',
    ])
    // each segment stands in a box of its own, the separator drawn before it
    expect(line.firstElementChild?.firstElementChild).toHaveClass('mono')
    expect(line.textContent).not.toMatch(/\p{Script=Hebrew}/u)
  })
})

describe('ChatScreen, every state', () => {
  // "The caret; tool steps appear as they run": a tool call's step line stands before the answer's first token
  it('Assistant · loading', async () => {
    serveSession()
    server.use(
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, ({ request }) => {
        const encoder = new TextEncoder()
        const body = new ReadableStream<Uint8Array>({
          start(controller) {
            controller.enqueue(encoder.encode(`event:tool\ndata:${JSON.stringify(whatIf)}\n\n`))
            // the answer has not begun; the stream ends only when the screen goes away
            request.signal.addEventListener('abort', () =>
              controller.error(new DOMException('aborted', 'AbortError')),
            )
          },
        })
        return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream' } })
      }),
    )
    renderScreen()

    await ask('האם בקשה 17 הייתה מאושרת אם היה ערב?')

    const answer = await lastAnswer()
    const steps = await within(answer).findByRole('list', { name: 'Tool calls' })
    expect(steps.querySelector('.chip--tool')).toHaveTextContent(
      'what-if · case 17 · has_guarantor=true',
    )
    // the caret stands under the step line while the answer is still to come
    expect(answer.querySelector('p.answer')).toHaveClass('streaming')
    expect(answer.querySelector('p.answer')).toHaveTextContent(/^$/)
  })
})

describe('ChatScreen · tool calls', () => {
  it('draws each tool call as a step line above its answer: the tool chip, what it ran on, the time and the outcome', async () => {
    serveSession()
    serveAnswer(
      answered(
        'כן, עם ערב הבקשה הייתה מאושרת.[[r:R-900]]',
        [{ id: 'r:R-900', kind: 'RULE', ruleId: 'R-900' }],
        {
          steps: [whatIf],
        },
      ),
    )
    renderScreen()

    await ask('האם בקשה 17 הייתה מאושרת אם היה ערב?')

    const answer = await lastAnswer()
    const steps = within(answer).getByRole('list', { name: 'Tool calls' })
    const [step] = within(steps).getAllByRole('listitem')
    expect(step!.querySelector('.chip--tool')).toHaveTextContent(
      'what-if · case 17 · has_guarantor=true',
    )
    expect(step).toHaveTextContent('ran on v1 · 58 µs')
    expect(within(step!).getByText('Approved')).toHaveClass('tag--approve')
    expect(step).toHaveTextContent('flag STABLE_INCOME_MANUAL_CHECK')
    // the step line stands above the answer that used it
    const body = answer.querySelector('.turn__body')!
    expect([...body.children].indexOf(steps)).toBeLessThan(
      [...body.children].indexOf(body.querySelector('p.answer')!),
    )
  })

  it('names a case it looked up, the decision it read, and a call it refused', async () => {
    serveSession()
    serveAnswer(
      answered(
        'בקשה 17 הופנתה לבדיקה.[[d:17]]',
        [{ id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer' }],
        {
          steps: [
            lookup,
            {
              ...lookup,
              applicationNumber: 999,
              micros: 310,
              outcome: null,
              decidingRuleId: null,
              refused: 'not_found',
            },
          ],
        },
      ),
    )
    renderScreen()

    await ask('למה בקשה מספר 17 הופנתה לבדיקה?')

    const [read, refused] = within(
      within(await lastAnswer()).getByRole('list', { name: 'Tool calls' }),
    ).getAllByRole('listitem')
    expect(read!.querySelector('.chip--tool')).toHaveTextContent('case 17')
    expect(read).toHaveTextContent('ran on v1 · 1.2 ms')
    expect(within(read!).getByText('Manual review')).toHaveClass('tag--refer')
    expect(refused!.querySelector('.chip--tool')).toHaveTextContent('case 999')
    expect(refused).toHaveTextContent('not in this session')
    expect(refused!.querySelector('.tag')).toBeNull()
  })
})

describe('ChatScreen · the system speaks', () => {
  it("marks the fixed not-covered sentence as the system's, with no source", async () => {
    serveSession()
    serveAnswer(answered(notCovered('he'), [], { fixed: 'not_covered' }))
    renderScreen()

    await ask('מהי הריבית המקסימלית שהבנק רשאי לגבות?')

    const answer = await lastAnswer()
    expect(answer.querySelector('.turn__who .actor--system')).not.toBeNull()
    expect(answer.querySelector('.turn__who .actor--model')).toBeNull()
    expect(answer.querySelector('.turn__head')).toHaveTextContent(/^System$/)
    expect(answer.querySelector('p.answer')).toHaveClass('answer--fixed')
    expect(
      within(answer).getByText("No source · a fixed sentence, not the model's"),
    ).toBeInTheDocument()
  })

  it("marks the tool-limit sentence as the system's too", async () => {
    serveSession()
    serveAnswer(
      answered('השאלה דורשת יותר בדיקות ממה שתשובה אחת רשאית לבצע.', [], {
        steps: [lookup],
        fixed: 'tool_limit',
      }),
    )
    renderScreen()

    await ask('למה בקשה מספר 17 הופנתה לבדיקה?')

    const answer = await lastAnswer()
    expect(answer.querySelector('.turn__who .actor--system')).not.toBeNull()
    expect(
      within(answer).getByText("No source · a fixed sentence, not the model's"),
    ).toBeInTheDocument()
  })

  it("shows a withheld answer as the amber note with the system's mark", async () => {
    serveSession()
    serveAnswer([['error', { code: 'ANSWER_WITHHELD' }]])
    renderScreen()

    await ask(TERM_QUESTION)

    const answer = await lastAnswer()
    expect(answer.querySelector('.turn__who .actor--system')).not.toBeNull()
    const note = answer.querySelector('.note--warning')!
    expect(note).toHaveTextContent(
      'The answer was withheld because it contained something that must not be shown.',
    )
  })

  it('shows the caret while an answer streams', async () => {
    serveSession()
    server.use(
      http.post(`${BASE}/chat/sessions/${SESSION}/messages`, ({ request }) => {
        const encoder = new TextEncoder()
        const body = new ReadableStream<Uint8Array>({
          start(controller) {
            controller.enqueue(
              encoder.encode(`event:token\ndata:${JSON.stringify({ text: 'תקופת ההחזר' })}\n\n`),
            )
            // the answer is still coming; the stream ends only when the screen goes away
            request.signal.addEventListener('abort', () =>
              controller.error(new DOMException('aborted', 'AbortError')),
            )
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
  it("says the day's budget is spent and when it resumes, and the composer stays open for a scripted question", async () => {
    serveSession()
    server.use(http.get(`${BASE}/system/budget`, () => HttpResponse.json(budgetSpent)))
    // the response cache answers a scripted question while the budget is spent (Document 2, GET /system/budget)
    serveAnswer(
      answered('תקופת ההחזר היא עד 84 חודשים.[[p:2]]', [
        { id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 },
      ]),
    )
    renderScreen()

    const note = await screen.findByRole('note', { name: 'Budget' })

    expect(note).toHaveTextContent(
      "Today's model budget is spent until 00:00. The demo's questions are still answered from the cache.",
    )
    // the owner's answer of 2026-09-28: the composer stays enabled, so the question can still be asked
    await ask(TERM_QUESTION)
    expect(await screen.findByText(/תקופת ההחזר היא עד/)).toBeVisible()
  })

  it('reads the budget again when a question finds it spent', async () => {
    serveSession()
    let spent = false
    server.use(
      http.get(`${BASE}/system/budget`, () => HttpResponse.json({ ...budgetSpent, spent })),
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
          : streamOf(
              answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]),
            )
      }),
    )
    renderScreen()
    const user = await ask(TERM_QUESTION)

    await user.click(
      within(await screen.findByRole('alert')).getByRole('button', { name: 'Try again' }),
    )

    await waitFor(() =>
      expect(turns().at(-1)!.querySelector('p.answer')).toHaveTextContent('84 months.'),
    )
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
    const group = screen.getByRole('group', { name: "The demo's questions" })
    for (const question of SCRIPTED_QUESTIONS) {
      expect(within(group).getByRole('button', { name: question })).toHaveClass('btn--quiet')
    }
    // numbered in the demo's order, the number for the eye alone
    expect(
      [...group.querySelectorAll('.starters__n')].map((number) => [
        number.textContent,
        number.getAttribute('aria-hidden'),
      ]),
    ).toEqual([
      ['1', 'true'],
      ['2', 'true'],
      ['3', 'true'],
    ])
  })

  // the spec, section 09 (v3.3): the opening names what the questions are about, in its own language, with the
  // version's line under it; the counts are the committed fixtures', which the API serves (Document 6)
  it("opens on the rule set's name, the published version with its paragraphs and rules, and the demo's questions", async () => {
    serveSession()
    renderScreen()

    const opening = await screen.findByRole('region', { name: 'Before the first question' })

    const name = within(opening).getByText('מדיניות אשראי צרכני')
    expect(name).toHaveAttribute('lang', 'he')
    expect(name).toHaveAttribute('dir', 'rtl')
    await waitFor(() =>
      expect(
        [...opening.querySelector('.prov')!.children].map((segment) => segment.textContent),
      ).toEqual([
        'Published v1',
        `${String(lendingParagraphs.length)} paragraphs`,
        `${String(lendingRuleSet.rules.length)} rules`,
      ]),
    )
    expect(opening.querySelector('.vstatus')).toHaveClass('vstatus--published')
    expect(within(opening).getByRole('group', { name: "The demo's questions" })).toBeInTheDocument()
    expect(opening.closest('[role="log"]')).toBeNull()
  })

  // the spec, section 09 (v3.3): the log scrolls under the foot, so the newest turn is brought into view when a question is
  // asked and as its answer arrives
  it('brings the newest turn into view when a question is asked and as its answer streams', async () => {
    serveSession()
    serveAnswer(answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]))
    renderScreen()
    const scrolled = vi.spyOn(Element.prototype, 'scrollIntoView')

    await ask(TERM_QUESTION)

    const answer = await lastAnswer()
    await waitFor(() => expect(answer.querySelector('p.answer')).toHaveTextContent('84 months.'))
    expect(scrolled.mock.contexts).toContain(answer)
    expect(
      scrolled.mock.calls.every(
        ([options]) => options === undefined || (options as { block?: string }).block === 'end',
      ),
    ).toBe(true)
    scrolled.mockRestore()
  })

  it('takes the opening away once a question is asked, and the thread stands in its place', async () => {
    serveSession()
    serveAnswer(answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]))
    renderScreen()
    await screen.findByRole('region', { name: 'Before the first question' })

    await ask(TERM_QUESTION)

    await lastAnswer()
    expect(
      screen.queryByRole('region', { name: 'Before the first question' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: "The demo's questions" })).not.toBeInTheDocument()
  })

  // the spec, section 09 (v3.3): the composer is the sheet's foot, the question in a box with its keys and its count
  it("keeps the composer at the sheet's foot, with the keys that ask and the count against the API's limit", async () => {
    serveSession()
    renderScreen()
    const user = userEvent.setup()

    const input = await screen.findByLabelText('Question')

    const thread = input.closest('.thread')!
    expect(thread.parentElement).toHaveClass('sheet--fill')
    const foot = thread.lastElementChild!
    expect(foot).toHaveClass('thread__foot')
    const composer = foot.querySelector('.composer')!
    expect(composer).toContainElement(input)
    expect(within(composer as HTMLElement).getByRole('button', { name: 'Ask' })).toHaveClass(
      'btn--primary',
    )
    expect(composer.querySelector('.composer__hint')).toHaveTextContent(
      /^↵ asks · ⇧ ↵ breaks the line$/,
    )
    expect([...composer.querySelectorAll('.kbd')].map((key) => key.textContent)).toEqual([
      '↵',
      '⇧ ↵',
    ])
    expect(within(composer as HTMLElement).getByText('0 / 1,000')).toHaveClass('counter')
    expect(input).toHaveAttribute('maxLength', '1000')

    await user.type(input, 'מה הריבית?')

    expect(within(composer as HTMLElement).getByText('10 / 1,000')).toHaveClass('counter')
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

/**
 * The sandbox's conversations (the spec, section 09, v3.3; Document 2, GET /chat/sessions and GET /chat/sessions/{id}):
 * listed beside the thread on a wide window, newest first, each by its first question; one opened again as it was
 * shown, asking on in its own session; a new one started at any time; a popover from the toolbar below 1200px.
 */
describe('ChatScreen · the conversations', () => {
  const EARLIER = '0f4c1c9e-0000-4000-8000-0000000000d2'
  const OLDEST = '0f4c1c9e-0000-4000-8000-0000000000d3'
  const listed: ChatSessionSummary[] = [
    {
      id: EARLIER,
      rulesetId: SEEDED_RULESET_ID,
      versionNo: 1,
      firstQuestion: TERM_QUESTION,
      turns: 1,
      openedAt: '2026-09-29T11:00:00Z',
      lastAt: '2026-09-29T11:05:00Z',
    },
    {
      id: OLDEST,
      rulesetId: SEEDED_RULESET_ID,
      versionNo: 1,
      firstQuestion: 'למה בקשה מספר 17 הופנתה לבדיקה?',
      turns: 3,
      openedAt: '2026-09-29T09:00:00Z',
      lastAt: '2026-09-29T09:12:00Z',
    },
  ]
  /** The oldest conversation as the API reads it back: case 17's referral, looked up and cited. */
  const oldest: ChatConversationResponse = {
    id: OLDEST,
    rulesetId: SEEDED_RULESET_ID,
    versionNo: 1,
    language: 'he',
    openedAt: '2026-09-29T09:00:00Z',
    turns: [
      {
        turn: 1,
        question: 'למה בקשה מספר 17 הופנתה לבדיקה?',
        askedAt: '2026-09-29T09:01:00Z',
        answer: 'בקשה 17 הופנתה לבדיקת חתם.[[d:17]]',
        answeredAt: '2026-09-29T09:01:04Z',
        citations: [{ id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer' }],
        toolCalls: [lookup],
        fixed: null,
      },
    ],
  }

  function windowOf(wide: boolean) {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: wide && query === '(min-width: 1200px)',
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }))
  }

  function serveConversations(sessions: ChatSessionSummary[]) {
    server.use(http.get(`${BASE}/chat/sessions`, () => HttpResponse.json({ sessions })))
  }

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists them beside the thread on a wide window, New conversation first, each by its first question with its version, its count and its time', async () => {
    windowOf(true)
    serveSession()
    serveConversations(listed)
    renderScreen()

    const margin = await screen.findByRole('complementary', { name: 'Conversations' })

    const rows = await within(margin).findAllByRole('button')
    expect(rows.map((row) => row.textContent)).toEqual([
      'New conversation',
      `${TERM_QUESTION}v1 · 1 question · 2026-09-29 11:05`,
      'למה בקשה מספר 17 הופנתה לבדיקה?v1 · 3 questions · 2026-09-29 09:12',
    ])
    const question = rows[1]!.querySelector('.conversation__q')!
    expect(question).toHaveAttribute('lang', 'he')
    expect(question).toHaveAttribute('dir', 'rtl')
    expect(rows[1]!.querySelector('.conversation__meta')).toHaveTextContent(
      /^v1 · 1 question · 2026-09-29 11:05$/,
    )
    // a new conversation with no question yet is not among them, so none is marked
    expect(margin.querySelector('[aria-current="true"]')).toBeNull()
  })

  it('opens an earlier conversation as it was shown, marks it, and asks on in its own session', async () => {
    windowOf(true)
    serveSession()
    serveConversations(listed)
    const opened: string[] = []
    server.use(
      http.get(`${BASE}/chat/sessions/${OLDEST}`, () => HttpResponse.json(oldest)),
      http.post(`${BASE}/chat/sessions/:id/messages`, ({ params }) => {
        opened.push(params.id as string)
        return streamOf(
          answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]),
        )
      }),
    )
    renderScreen()
    const user = userEvent.setup()
    const margin = await screen.findByRole('complementary', { name: 'Conversations' })

    await user.click(
      await within(margin).findByRole('button', { name: /^למה בקשה מספר 17 הופנתה לבדיקה\?/ }),
    )

    // the thread as it was shown: the question, the step line, the answer with its chip, the sources strip
    const answer = await lastAnswer()
    expect(turns()[0]!.querySelector('.turn__body')).toHaveTextContent(
      'למה בקשה מספר 17 הופנתה לבדיקה?',
    )
    expect(within(answer).getByRole('list', { name: 'Tool calls' })).toHaveTextContent(
      'case 17ran on v1 · 1.2 msManual review',
    )
    expect(
      within(answer.querySelector<HTMLElement>('p.answer')!).getByRole('button', {
        name: 'Case 17',
      }),
    ).toBeInTheDocument()
    expect(
      within(within(answer).getByRole('group', { name: 'Sources' })).getByRole('button', {
        name: 'Case 17',
      }),
    ).toBeInTheDocument()
    expect(answer.querySelector('p.answer')).not.toHaveClass('streaming')
    expect(
      screen.queryByRole('region', { name: 'Before the first question' }),
    ).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('complementary', { name: 'Conversations' })).getByRole('button', {
        name: /^למה בקשה מספר 17/,
      }),
    ).toHaveAttribute('aria-current', 'true')

    // the next question goes to the conversation's own session, and no session is opened for it
    await ask(TERM_QUESTION)
    await waitFor(() => expect(turns()).toHaveLength(4))
    expect(opened).toEqual([OLDEST])
  })

  it('starts a new conversation, and the one left stays listed, no longer marked', async () => {
    windowOf(true)
    const sessions: ChatSessionSummary[] = []
    let openings = 0
    server.use(
      http.post(`${BASE}/chat/sessions`, () => {
        openings++
        return HttpResponse.json(
          {
            id: openings === 1 ? SESSION : EARLIER,
            rulesetId: SEEDED_RULESET_ID,
            versionNo: 1,
            language: 'he',
          },
          { status: 201 },
        )
      }),
      http.get(`${BASE}/chat/sessions`, () => HttpResponse.json({ sessions })),
      http.post(`${BASE}/chat/sessions/:id/messages`, ({ params }) => {
        sessions.unshift({
          id: params.id as string,
          rulesetId: SEEDED_RULESET_ID,
          versionNo: 1,
          firstQuestion: TERM_QUESTION,
          turns: 1,
          openedAt: '2026-09-29T09:00:00Z',
          lastAt: '2026-09-29T09:01:00Z',
        })
        return streamOf(
          answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]),
        )
      }),
    )
    renderScreen()
    const user = await ask(TERM_QUESTION)
    await lastAnswer()

    // once answered, the conversation is listed and marked as the open one
    const margin = screen.getByRole('complementary', { name: 'Conversations' })
    const row = await within(margin).findByRole('button', { name: new RegExp(`^${TERM_QUESTION}`) })
    expect(row).toHaveAttribute('aria-current', 'true')

    await user.click(within(margin).getByRole('button', { name: 'New conversation' }))

    expect(
      await screen.findByRole('region', { name: 'Before the first question' }),
    ).toBeInTheDocument()
    expect(turns()).toHaveLength(0)
    await waitFor(() => expect(openings).toBe(2))
    expect(
      within(screen.getByRole('complementary', { name: 'Conversations' })).getByRole('button', {
        name: new RegExp(`^${TERM_QUESTION}`),
      }),
    ).not.toHaveAttribute('aria-current')
  })

  it('takes New conversation in a conversation with no question yet as a request for the question', async () => {
    windowOf(true)
    serveSession()
    const openings: number[] = []
    server.use(
      http.post(`${BASE}/chat/sessions`, () => {
        openings.push(1)
        return HttpResponse.json(
          { id: SESSION, rulesetId: SEEDED_RULESET_ID, versionNo: 1, language: 'he' },
          { status: 201 },
        )
      }),
    )
    renderScreen()
    const user = userEvent.setup()
    const margin = await screen.findByRole('complementary', { name: 'Conversations' })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Ask' })).toBeDisabled())

    await user.click(within(margin).getByRole('button', { name: 'New conversation' }))

    expect(screen.getByLabelText('Question')).toHaveFocus()
    expect(openings).toHaveLength(1)
  })

  it('opens them from the toolbar in a popover below 1200px, which choosing one closes', async () => {
    windowOf(false)
    serveSession()
    serveConversations(listed)
    server.use(http.get(`${BASE}/chat/sessions/${OLDEST}`, () => HttpResponse.json(oldest)))
    renderScreen()
    const user = userEvent.setup()
    expect(screen.queryByRole('complementary', { name: 'Conversations' })).not.toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: 'Conversations · 2' }))

    const popover = screen.getByRole('dialog', { name: 'Conversations' })
    expect(
      within(popover)
        .getAllByRole('button')
        .map((row) => row.textContent),
    ).toEqual([
      'New conversation',
      `${TERM_QUESTION}v1 · 1 question · 2026-09-29 11:05`,
      'למה בקשה מספר 17 הופנתה לבדיקה?v1 · 3 questions · 2026-09-29 09:12',
    ])

    await user.click(within(popover).getByRole('button', { name: /^למה בקשה מספר 17/ }))

    const answer = await lastAnswer()
    expect(screen.queryByRole('dialog', { name: 'Conversations' })).not.toBeInTheDocument()
    expect(
      within(answer.querySelector<HTMLElement>('p.answer')!).getByRole('button', {
        name: 'Case 17',
      }),
    ).toBeInTheDocument()
  })

  it('says when a conversation is no longer available, and offers a new one', async () => {
    windowOf(true)
    serveSession()
    serveConversations(listed)
    renderScreen()
    const user = userEvent.setup()
    const margin = await screen.findByRole('complementary', { name: 'Conversations' })

    // the default handler answers 404 for any conversation read back: gone with its sandbox's reset
    await user.click(within(margin).getByRole('button', { name: /^למה בקשה מספר 17/ }))

    expect(await screen.findByText('This conversation is no longer available')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'New conversation' }))
    expect(
      await screen.findByRole('region', { name: 'Before the first question' }),
    ).toBeInTheDocument()
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
          {
            id: 'd:17',
            kind: 'DECISION',
            applicationNumber: 17,
            outcome: 'refer',
            ruleId: 'R-330',
          },
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
    serveAnswer(
      answered('The minimum age is 21.[[p:1]]', [{ id: 'p:1', kind: 'PARAGRAPH', paragraph: 1 }]),
    )
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

    expect(thread).toHaveLength(43)
    expect(unported(stylesheet('features/chat/ChatScreen.css'), thread)).toEqual([])
    expect(stylesheet('features/chat/ChatScreen.css')).toContain('@keyframes blink')
  })

  // the spec (v3.3): the log scrolls under the foot, and the thread's column is centred at the measure it sets
  it('scrolls the log on its own under the foot, the column centred, and the foot on paper', () => {
    const css = stylesheet('features/chat/ChatScreen.css')

    expect(rule(css, '.thread__scroll')).toMatchObject({ flex: '1', overflow: 'auto' })
    expect(rule(css, '.thread__foot')).toMatchObject({
      'margin-top': 'auto',
      background: 'var(--paper)',
    })
    expect(rule(css, '.thread__log')).toMatchObject({
      'max-width': '880px',
      'margin-inline': 'auto',
    })
    expect(rule(css, '.composer:focus-within')['border-color']).toBe('var(--focus)')
    // an English thread keeps its step lines at its own start edge (NFR-5)
    expect(rule(css, ".thread[dir='ltr'] .steps")['justify-content']).toBe('flex-start')
  })

  // The new-policy walk of 2026-10-01: a what-if whose change is six fields, written without a space, widened the turn
  // past the log and cut off the start of the answer under it; the chip breaks such a run anywhere instead
  it("breaks a tool chip's run without a space rather than widen the turn", () => {
    const css = stylesheet('features/chat/ChatScreen.css')

    expect(rule(css, '.steps .chip--tool')).toMatchObject({
      'max-width': '100%',
      'white-space': 'normal',
      'overflow-wrap': 'anywhere',
    })
  })
})

/**
 * Before the conversation (the spec, section 11, the Assistant row, v3.9): the header stands while the rule sets are
 * read, a list that cannot be read says so where the screen said there was no published version, and a conversation
 * that does not open says that, where the note said a question could not be answered before any was asked.
 */
describe('ChatScreen · what it opens on', () => {
  it('draws its header while the rule sets are read', async () => {
    let release: () => void = () => undefined
    const read = new Promise<void>((resolve) => {
      release = resolve
    })
    serveSession()
    server.use(
      http.get(`${BASE}/rulesets`, async () => {
        await read
        return HttpResponse.json({
          rulesets: [
            {
              id: SEEDED_RULESET_ID,
              name: lendingRuleSet.name,
              domain: lendingRuleSet.id,
              protected: true,
              versions: [{ versionNo: 1, status: 'PUBLISHED' }],
            },
          ],
        })
      }),
    )
    renderScreen()

    expect(await screen.findByText('Loading the rule sets')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Assistant' })).toBeInTheDocument()
    release()
    expect(await screen.findByLabelText('Question')).toBeInTheDocument()
  })

  it('Assistant · a list that cannot be read', async () => {
    server.use(
      http.get(`${BASE}/rulesets`, () =>
        HttpResponse.json(
          {
            code: 'INTERNAL_ERROR',
            message: 'The request could not be completed.',
            details: [],
            traceId: 't',
          },
          { status: 500 },
        ),
      ),
    )
    renderScreen()

    expect(await screen.findByText('The rule sets could not be read.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    expect(screen.queryByText('No published version to ask about')).not.toBeInTheDocument()
  })

  it('says when the conversation could not be opened, and opens it again on request', async () => {
    let opened = 0
    server.use(
      http.post(`${BASE}/chat/sessions`, () => {
        opened += 1
        return opened === 1
          ? HttpResponse.error()
          : HttpResponse.json(
              { id: SESSION, rulesetId: SEEDED_RULESET_ID, versionNo: 1, language: 'he' },
              { status: 201 },
            )
      }),
    )
    renderScreen()

    const note = await screen.findByRole('alert')
    expect(note).toHaveTextContent(
      /^The conversation could not be opened\. Try again in a moment\. Try again$/,
    )
    await userEvent.click(within(note).getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
    expect(opened).toBe(2)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Ask' })).toBeDisabled())
  })
})

/**
 * The cited paragraph below 1200px (the spec, sections 08 and 10, v3.9): a drawer that Esc shuts, as every drawer is;
 * on a phone the next section of the page, under the thread and the composer, brought into view when a chip opens it.
 */
describe('ChatScreen · the cited paragraph below 1200px', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  async function openParagraph() {
    serveSession()
    serveAnswer(answered('84 months.[[p:2]]', [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }]))
    renderScreen()
    const user = await ask(TERM_QUESTION)
    const [inline] = await within(await lastAnswer()).findAllByRole('button', {
      name: 'Paragraph 2',
    })
    await user.click(inline!)
    return user
  }

  it('shuts the drawer of the paragraph on Esc', async () => {
    windowAt(1024)
    const user = await openParagraph()
    expect(screen.getByRole('complementary', { name: 'Paragraph 2' })).toBeInTheDocument()

    await user.keyboard('{Escape}')

    expect(screen.queryByRole('complementary', { name: 'Paragraph 2' })).not.toBeInTheDocument()
  })

  it('on a phone brings the paragraph into view under the composer', async () => {
    windowAt(390)
    const shown = vi.spyOn(Element.prototype, 'scrollIntoView')
    vi.stubGlobal('scrollTo', vi.fn())
    await openParagraph()

    expect(shown.mock.contexts).toContain(
      screen.getByRole('complementary', { name: 'Paragraph 2' }),
    )
  })
})
