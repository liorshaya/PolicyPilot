import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { expect, test, type Page } from '@playwright/test'
import { RULESET_ID, serveTheSeededRuleSet } from './seeded'

/**
 * Demo step 3 in a real browser (Document 1, Demo script; Document 6, End to end): the three scripted questions are
 * answered as they stream, each with its markers as chips that open what they cite, and the fourth gets the fixed
 * not-covered sentence. The questions and their markers are the labeled set's (fixtures/eval/questions.json, Q-01 to
 * Q-04); the streams are what the API sends for them (Document 2, POST /chat/sessions/{id}/messages).
 */

interface LabeledQuestion {
  id: string
  question: string
  expectedMarkers: string[]
}

const labeled = (
  JSON.parse(
    readFileSync(
      fileURLToPath(new URL('../../fixtures/eval/questions.json', import.meta.url)),
      'utf8',
    ),
  ) as { questions: LabeledQuestion[] }
).questions

function question(id: string): LabeledQuestion {
  return labeled.find((entry) => entry.id === id)!
}

const SESSION = '0f4c1c9e-0000-4000-8000-0000000000d1'
const NOT_COVERED_HE = 'המסמכים אינם עוסקים בשאלה הזו; אפשר לשאול על כלל, על סעיף או על מספר בקשה.'
const SIMULATION = '[[sim:d17:has_guarantor=true]]'

/** One answer per scripted question, with the citations the API resolved for its markers. */
const answers: Record<string, { text: string; citations: unknown[] }> = {
  'Q-01': {
    text: 'בקשה 17 הופנתה לבדיקת חתם: נרשם לה אירוע אשראי שלילי אחד ולא הועמד ערב.[[d:17]][[p:7]]',
    citations: [
      { id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer', ruleId: 'R-330' },
      { id: 'p:7', kind: 'PARAGRAPH', paragraph: 7 },
    ],
  },
  'Q-02': {
    text: `כן, עם ערב הבקשה הייתה מאושרת.${SIMULATION} האישור ניתן לפי הכלל R-900.[[r:R-900]]`,
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
    text: 'תקופת ההחזר המקסימלית היא 84 חודשים.[[p:2]]',
    citations: [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }],
  },
  'Q-04': { text: NOT_COVERED_HE, citations: [] },
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

async function ask(page: Page, text: string): Promise<void> {
  await page.getByLabel('Question').fill(text)
  await page.getByRole('button', { name: 'Ask' }).click()
}

test.describe('the assistant', () => {
  test.beforeEach(async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await serveTheChat(page)
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await page
      .getByRole('navigation', { name: 'Workspace' })
      .getByRole('button', { name: 'Assistant' })
      .click()
    await expect(page.getByRole('button', { name: 'Ask' })).toBeDisabled()
  })

  test('answers why application 17 was referred, and its paragraph opens beside it', async ({
    page,
  }) => {
    // Q-01 expects [[d:17]] and [[p:7]]
    expect(question('Q-01').expectedMarkers).toEqual(['[[d:17]]', '[[p:7]]'])
    await ask(page, question('Q-01').question)

    const answer = page.locator('p[dir="rtl"][lang="he"]').filter({ hasText: 'ערב' })
    await expect(answer).toBeVisible()
    await expect(answer.getByRole('button', { name: 'Application 17' })).toBeVisible()

    await answer.getByRole('button', { name: '¶ 7' }).click()

    const source = page.getByRole('complementary', { name: 'Paragraph 7' })
    await expect(source).toContainText('מבקש עם אירוע אחד יידרש להעמיד ערב')
  })

  test('the decision chip opens the cases screen', async ({ page }) => {
    await ask(page, question('Q-01').question)

    await page.getByRole('button', { name: 'Application 17' }).click()

    await expect(page.getByRole('heading', { level: 1, name: 'Cases' })).toBeVisible()
  })

  test('answers the guarantor question with the simulation, and the rule chip opens its rule', async ({
    page,
  }) => {
    // Q-02 expects a simulation marker and [[r:R-900]]
    expect(question('Q-02').expectedMarkers).toEqual(['[[sim:*]]', '[[r:R-900]]'])
    await ask(page, question('Q-02').question)

    // a simulation opens nothing, so its chip is text that says what the engine simulated
    const chip = page.getByText('What if has_guarantor=true', { exact: true })
    await expect(chip).toHaveAttribute(
      'title',
      'If has_guarantor=true: approve, as the engine simulated',
    )

    await page.getByRole('button', { name: 'R-900' }).click()

    await expect(page.getByRole('heading', { level: 1, name: 'Rules' })).toBeVisible()
    await expect(page.getByText('Paragraph 9 is the source of R-900')).toBeVisible()
  })

  test('answers the term question from paragraph 2, then refuses the rate question with the fixed sentence', async ({
    page,
  }) => {
    expect(question('Q-03').expectedMarkers).toEqual(['[[p:2]]'])
    await ask(page, question('Q-03').question)
    await expect(page.getByText('תקופת ההחזר המקסימלית היא 84 חודשים.')).toBeVisible()
    await expect(page.getByRole('button', { name: '¶ 2' })).toBeVisible()

    expect(question('Q-04').expectedMarkers).toEqual([])
    await ask(page, question('Q-04').question)

    const refusal = page.getByText(NOT_COVERED_HE)
    await expect(refusal).toBeVisible()
    await expect(refusal.locator('xpath=ancestor::p[1]').getByRole('button')).toHaveCount(0)
  })
})
