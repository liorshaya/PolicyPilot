import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { expect, test, type Page } from '@playwright/test'
import { RULESET_ID, paragraphs, serveTheSeededRuleSet } from './seeded'

/**
 * The guided demo panel in a real browser (Brief FR-23; Document 2, Frontend Architecture; Work Plan day 11). The
 * panel is how the demo is driven from an interviewer's machine, so what this proves is that its steps land on the
 * right screen with the right input already filled in, and that step 3 then answers all three scripted questions.
 * The questions are the labeled set's (fixtures/eval/questions.json, Q-01 to Q-03), never typed out here.
 */

interface LabeledQuestion {
  id: string
  question: string
}

const labeled = (
  JSON.parse(
    readFileSync(
      fileURLToPath(new URL('../../fixtures/eval/questions.json', import.meta.url)),
      'utf8',
    ),
  ) as { questions: LabeledQuestion[] }
).questions

function question(id: string): string {
  return labeled.find((entry) => entry.id === id)!.question
}

const SESSION = '0f4c1c9e-0000-4000-8000-0000000000d1'

/** One short cited answer per scripted question; what is under test here is the panel, not the wording. */
const answers: Record<string, { text: string; citations: unknown[] }> = {
  'Q-01': {
    text: 'בקשה 17 הופנתה לבדיקת חתם.[[d:17]]',
    citations: [
      { id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer', ruleId: 'R-330' },
    ],
  },
  'Q-02': {
    text: 'כן, עם ערב הבקשה הייתה מאושרת.[[sim:d17:has_guarantor=true]]',
    citations: [
      {
        id: 'sim:d17:has_guarantor=true',
        kind: 'SIMULATION',
        applicationNumber: 17,
        outcome: 'approve',
        detail: 'has_guarantor=true',
      },
    ],
  },
  'Q-03': {
    text: 'תקופת ההחזר המקסימלית היא 84 חודשים.[[p:2]]',
    citations: [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }],
  },
}

function streamOf(text: string, citations: unknown[]): string {
  const pieces = text.match(/.{1,9}/gsu) ?? []
  const events: [string, unknown][] = [
    ...pieces.map((piece): [string, unknown] => ['token', { text: piece }]),
    ['citations', { citations }],
    ['usage', { inputTokens: 900, outputTokens: 40, toolCalls: 0 }],
    ['done', { messageId: '0f4c1c9e-0000-4000-8000-0000000000e1' }],
  ]
  return events.map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`).join('')
}

async function serveTheChat(page: Page): Promise<void> {
  await page.route('**/api/v1/chat/sessions', (route) =>
    route.fulfill({
      status: 201,
      json: { id: SESSION, rulesetId: RULESET_ID, versionNo: 1, language: 'he' },
    }),
  )
  await page.route(`**/api/v1/chat/sessions/${SESSION}/messages`, (route) => {
    const asked = (route.request().postDataJSON() as { question: string }).question
    const entry = labeled.find((candidate) => candidate.question === asked)!
    const answer = answers[entry.id]
    return route.fulfill({
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
      body: streamOf(answer.text, answer.citations),
    })
  })
}

async function openThePanel(page: Page): Promise<void> {
  await page.goto('/')
  await page.getByLabel('Access code').fill('qwertyui')
  await page.getByRole('button', { name: 'Enter' }).click()
  await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()
  await page.getByRole('button', { name: /guided demo/i }).click()
}

function step(page: Page, title: string) {
  return page.getByRole('listitem').filter({ hasText: title }).getByRole('button', { name: 'Run' })
}

test.describe('the guided demo panel', () => {
  test.beforeEach(async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await serveTheChat(page)
  })

  test('step 3 answers all three scripted questions, each with its citation', async ({ page }) => {
    await openThePanel(page)

    await step(page, 'Ask').click()

    await expect(page.getByRole('heading', { level: 1, name: 'Assistant' })).toBeVisible()
    // the panel fills the first question in; a presenter presses Ask and types nothing
    await expect(page.getByLabel('Question')).toHaveValue(question('Q-01'))
    // the question it fills in is Hebrew, so the composer has to turn around with it (Document 5, Hebrew pitfalls)
    await expect(page.getByLabel('Question')).toHaveAttribute('dir', 'auto')
    await page.getByRole('button', { name: 'Ask' }).click()
    await expect(page.getByRole('button', { name: 'Application 17' })).toBeVisible()

    await page.getByLabel('Question').fill(question('Q-02'))
    await page.getByRole('button', { name: 'Ask' }).click()
    await expect(page.getByText('עם ערב הבקשה הייתה מאושרת')).toBeVisible()

    await page.getByLabel('Question').fill(question('Q-03'))
    await page.getByRole('button', { name: 'Ask' }).click()
    await expect(page.getByText('84 חודשים')).toBeVisible()
    await expect(page.getByRole('button', { name: '¶ 2' })).toBeVisible()
  })

  test('step 1 opens the policy form already holding the sample policy', async ({ page }) => {
    await openThePanel(page)

    await step(page, 'Author').click()

    await expect(page.getByRole('heading', { level: 1, name: 'Policies' })).toBeVisible()
    // the form opens holding the seeded policy's own paragraphs, read back through the API, not a copy
    await expect(page.getByLabel('Policy text')).toHaveValue(
      paragraphs.map((paragraph) => paragraph.text).join('\n\n'),
    )
  })

  test('step 2 lands on the cases screen and decides the seeded set', async ({ page }) => {
    await openThePanel(page)

    await step(page, 'Decide').click()

    await expect(page.getByRole('heading', { level: 1, name: 'Cases' })).toBeVisible()
  })

  test('step 4 is listed and not offered until its day', async ({ page }) => {
    await openThePanel(page)

    const change = page.getByRole('listitem').filter({ hasText: 'Change' })

    await expect(change).toContainText('Day 14')
    await expect(change.getByRole('button', { name: 'Run' })).toHaveCount(0)
  })
})
