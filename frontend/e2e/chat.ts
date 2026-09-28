import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'
import { RULESET_ID } from './seeded'

/**
 * Demo step 3 as the tests serve it (Document 2, POST /chat/sessions/{id}/messages): the labeled questions
 * (fixtures/eval/questions.json, Q-01 to Q-04), one answer for each with the tool calls it made and the citations the
 * API resolved for its markers, and the stream the API sends them in: a `tool` event per call before the first token,
 * and `done` naming the fixed sentence an answer is. The not-covered sentence is read from the API's own file,
 * prompts/answer/not-covered.yml, so the browser is shown exactly what the API would send.
 */

export interface LabeledQuestion {
  id: string
  question: string
  expectedMarkers: string[]
}

function repositoryFile(path: string): string {
  return readFileSync(fileURLToPath(new URL(`../../${path}`, import.meta.url)), 'utf8')
}

const labeled = (
  JSON.parse(repositoryFile('fixtures/eval/questions.json')) as { questions: LabeledQuestion[] }
).questions

export function question(id: string): LabeledQuestion {
  return labeled.find((entry) => entry.id === id)!
}

/** Document 4's fixed sentence in one language, as the API holds it (a quoted string on its own line). */
export function notCovered(language: 'he' | 'en'): string {
  const line = repositoryFile('backend/src/main/resources/prompts/answer/not-covered.yml')
    .split('\n')
    .find((candidate) => candidate.startsWith(`${language}: `))!
  return JSON.parse(line.slice(language.length + 2)) as string
}

export const SESSION = '0f4c1c9e-0000-4000-8000-0000000000d1'
const SIMULATION = '[[sim:d17:has_guarantor=true]]'

/** Case 17 as getDecision reads it: referred by R-330, no flag (Document 3's worked example). */
const lookup = {
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
const whatIf = {
  ...lookup,
  tool: 'simulate',
  overrides: 'has_guarantor=true',
  micros: 58,
  outcome: 'approve',
  decidingRuleId: 'R-900',
  flags: ['STABLE_INCOME_MANUAL_CHECK'],
}

interface Answer {
  text: string
  citations: unknown[]
  /** The tool calls the answer made, each sent as it ends, before the first token. */
  steps?: unknown[]
  /** The fixed sentence the answer is, which `done` names (Document 4): the model wrote none of it. */
  fixed?: 'not_covered' | 'tool_limit'
}

/**
 * One answer per scripted question, with the tool calls it made and the citations the API resolved for its markers.
 * A sentence's sources follow it one after another with a space between them, as Document 4's answer prompt writes
 * them ("[[d:17]] [[r:R-330]] [[p:7]]").
 */
const answers: Record<string, Answer> = {
  'Q-01': {
    text: 'בקשה 17 הופנתה לבדיקת חתם: נרשם לה אירוע אשראי שלילי אחד ולא הועמד ערב. [[d:17]] [[p:7]]',
    steps: [lookup],
    citations: [
      { id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer', ruleId: 'R-330' },
      { id: 'p:7', kind: 'PARAGRAPH', paragraph: 7 },
    ],
  },
  'Q-02': {
    text: `כן, עם ערב הבקשה הייתה מאושרת. ${SIMULATION} האישור ניתן לפי הכלל R-900. [[r:R-900]]`,
    steps: [whatIf],
    citations: [
      {
        id: SIMULATION.slice(2, -2),
        kind: 'SIMULATION',
        applicationNumber: 17,
        outcome: 'approve',
        detail: 'has_guarantor=true',
      },
      { id: 'r:R-900', kind: 'RULE', ruleId: 'R-900', paragraph: 9, label: 'אישור' },
    ],
  },
  'Q-03': {
    text: 'תקופת ההחזר המקסימלית היא 84 חודשים. [[p:2]]',
    citations: [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }],
  },
  'Q-04': { text: notCovered('he'), citations: [], fixed: 'not_covered' },
}

function streamOf({ text, citations, steps = [], fixed }: Answer): string {
  const pieces = text.match(/.{1,9}/gsu) ?? []
  const events: [string, unknown][] = [
    ...steps.map((step): [string, unknown] => ['tool', step]),
    ...pieces.map((piece): [string, unknown] => ['token', { text: piece }]),
    ['citations', { citations }],
    ['usage', { inputTokens: 900, outputTokens: 40, toolCalls: steps.length }],
    ['done', { messageId: '0f4c1c9e-0000-4000-8000-0000000000e1', fixed: fixed ?? null }],
  ]
  return events.map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`).join('')
}

/** Serves a chat session on the seeded version and a streamed answer to each labeled question it is asked. */
export async function serveTheChat(page: Page): Promise<void> {
  await page.route('**/api/v1/chat/sessions', (route) =>
    route.fulfill({
      status: 201,
      json: { id: SESSION, rulesetId: RULESET_ID, versionNo: 1, language: 'he' },
    }),
  )
  await page.route(`**/api/v1/chat/sessions/${SESSION}/messages`, (route) => {
    const asked = (route.request().postDataJSON() as { question: string }).question
    const entry = labeled.find((candidate) => candidate.question === asked)!
    return route.fulfill({
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
      body: streamOf(answers[entry.id]),
    })
  })
}

export async function ask(page: Page, text: string): Promise<void> {
  await page.getByLabel('Question', { exact: true }).fill(text)
  await page.getByRole('button', { name: 'Ask' }).click()
}
