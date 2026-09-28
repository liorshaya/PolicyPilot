import { expect, test } from '@playwright/test'
import { ask, notCovered, question, serveTheChat } from './chat'
import { serveTheSeededRuleSet } from './seeded'

/**
 * Demo step 3 in a real browser (Document 1, Demo script; Document 6, End to end): the three scripted questions are
 * answered as they stream, each tool call a step line above its answer and each marker a chip that opens what it cites,
 * and the fourth gets the fixed not-covered sentence. The questions and their markers are the labeled set's
 * (fixtures/eval/questions.json, Q-01 to Q-04); the streams are what the API sends for them (Document 2, POST
 * /chat/sessions/{id}/messages). The chips read "Case 17" and "Paragraph 7", the spec's glossary (Register phase 4).
 */

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

  test('answers why case 17 was referred, and its paragraph opens beside it', async ({ page }) => {
    // Q-01 expects [[d:17]] and [[p:7]]
    expect(question('Q-01').expectedMarkers).toEqual(['[[d:17]]', '[[p:7]]'])
    await ask(page, question('Q-01').question)

    const answer = page.locator('p[dir="rtl"][lang="he"]').filter({ hasText: 'ערב' })
    await expect(answer).toBeVisible()
    // one chip per claim inline, the first of its sources (the spec, section 09); the sources strip holds them all
    await expect(answer.getByRole('button')).toHaveCount(1)
    await expect(answer.getByRole('button', { name: 'Case 17' })).toBeVisible()
    // the lookup the answer made, as a step line above it with the engine's outcome
    const step = page.getByRole('list', { name: 'Tool calls' }).getByRole('listitem')
    await expect(step).toContainText('case 17')
    await expect(step.locator('.tag--refer')).toHaveText('Manual review')

    // one chip per claim inline; the sources strip repeats them all (the spec, section 09)
    await page
      .getByRole('group', { name: 'Sources' })
      .getByRole('button', { name: 'Paragraph 7' })
      .click()

    const source = page.getByRole('complementary', { name: 'Paragraph 7' })
    await expect(source).toContainText('מבקש עם אירוע אחד יידרש להעמיד ערב')
  })

  test('the decision chip opens the cases screen', async ({ page }) => {
    await ask(page, question('Q-01').question)

    await page
      .getByRole('group', { name: 'Sources' })
      .getByRole('button', { name: 'Case 17' })
      .click()

    await expect(page.getByRole('heading', { level: 1, name: 'Cases' })).toBeVisible()
  })

  test('answers the guarantor question with the simulation, and the rule chip opens its rule', async ({
    page,
  }) => {
    // Q-02 expects a simulation marker and [[r:R-900]]
    expect(question('Q-02').expectedMarkers).toEqual(['[[sim:*]]', '[[r:R-900]]'])
    await ask(page, question('Q-02').question)

    // a simulation opens nothing, so its chip is the tool's, whose title says what the engine simulated
    const chip = page.locator('p[dir="rtl"][lang="he"]').getByText('what-if', { exact: true })
    await expect(chip).toHaveAttribute(
      'title',
      'What if has_guarantor=true: Approved, as the engine simulated',
    )
    await expect(page.getByRole('list', { name: 'Tool calls' })).toContainText(
      'what-if · case 17 · has_guarantor=true',
    )

    await page
      .getByRole('group', { name: 'Sources' })
      .getByRole('button', { name: 'R-900' })
      .click()

    await expect(page.getByRole('heading', { level: 1, name: 'Rules' })).toBeVisible()
    await expect(page.getByText('Paragraph 9 is the source of R-900')).toBeVisible()
  })

  test('answers the term question from paragraph 2, then refuses the rate question with the fixed sentence', async ({
    page,
  }) => {
    expect(question('Q-03').expectedMarkers).toEqual(['[[p:2]]'])
    await ask(page, question('Q-03').question)
    await expect(page.getByText('תקופת ההחזר המקסימלית היא 84 חודשים.')).toBeVisible()
    await expect(
      page.getByRole('group', { name: 'Sources' }).getByRole('button', { name: 'Paragraph 2' }),
    ).toBeVisible()

    expect(question('Q-04').expectedMarkers).toEqual([])
    await ask(page, question('Q-04').question)

    const refusal = page.getByText(notCovered('he'))
    await expect(refusal).toBeVisible()
    await expect(refusal.locator('xpath=ancestor::p[1]').getByRole('button')).toHaveCount(0)
    // the stream names it as Document 4's fixed sentence, which the system says and no source backs
    await expect(page.getByText("No source · a fixed sentence, not the model's")).toBeVisible()
  })
})
