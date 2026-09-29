import { expect, test } from '@playwright/test'
import { ask, notCovered, question, serveTheChat } from './chat'
import { paragraphs, ruleSet, serveASeededRun, serveTheSeededRuleSet } from './seeded'

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

  // the Register spec, section 09 (v3.3): the composer is the sheet's foot and the log scrolls under it, following the
  // newest answer; the opening stands before the first question and goes with it
  test("opens on the demo's questions, and keeps the newest answer in view above the composer", async ({
    page,
  }) => {
    const opening = page.getByRole('region', { name: 'Before the first question' })
    await expect(opening.getByRole('group', { name: "The demo's questions" })).toBeVisible()
    // the version's line, its separators drawn by the stylesheet; the counts are what the stubs serve
    await expect(opening.locator('.prov > *')).toHaveText([
      'Published v1',
      `${String(paragraphs.length)} paragraphs`,
      `${String(ruleSet.rules.length)} rules`,
    ])

    for (const id of ['Q-01', 'Q-02', 'Q-03', 'Q-04']) {
      await ask(page, question(id).question)
    }

    await expect(opening).toHaveCount(0)
    const newest = page.getByText(notCovered('he'))
    await expect(newest).toBeVisible()
    await expect(newest).toBeInViewport()
    await expect(page.getByRole('button', { name: 'Ask' })).toBeInViewport()
    await expect(page.getByRole('log').locator('.turn').first()).not.toBeInViewport()
  })

  // Document 2, GET /chat/sessions and GET /chat/sessions/{id} (2026-09-29); the spec, section 09: the conversations
  // beside the thread, an earlier one opened as it was shown and asked on, a new one started empty
  test('keeps every conversation: an earlier one opens as it was shown and asks on, a new one starts empty', async ({
    page,
  }) => {
    await ask(page, question('Q-01').question)
    await expect(
      page.getByRole('group', { name: 'Sources' }).getByRole('button', { name: 'Case 17' }),
    ).toBeVisible()
    const list = page.getByRole('complementary', { name: 'Conversations' })
    const rows = list.locator('.conversation:not(.conversation--new)')
    await expect(rows).toHaveCount(1)
    await expect(rows.first()).toHaveAttribute('aria-current', 'true')
    await expect(rows.first().locator('.conversation__meta')).toHaveText(
      /^v1 · 1 question · 2026-09-29 09:\d\d$/,
    )

    await list.getByRole('button', { name: 'New conversation' }).click()

    await expect(page.getByRole('region', { name: 'Before the first question' })).toBeVisible()
    await ask(page, question('Q-03').question)
    await expect(
      page.getByRole('group', { name: 'Sources' }).getByRole('button', { name: 'Paragraph 2' }),
    ).toBeVisible()
    // newest first, the open one marked
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(0)).toContainText(question('Q-03').question)
    await expect(rows.nth(0)).toHaveAttribute('aria-current', 'true')
    await expect(rows.nth(1)).toContainText(question('Q-01').question)

    await rows.nth(1).click()

    // the first conversation as it was shown, then asked on in its own session
    await expect(page.getByRole('log').locator('.turn--q').first()).toContainText(
      question('Q-01').question,
    )
    await expect(page.getByRole('list', { name: 'Tool calls' })).toContainText('case 17')
    await expect(rows.nth(1)).toHaveAttribute('aria-current', 'true')
    await ask(page, question('Q-02').question)
    await expect(page.getByRole('list', { name: 'Tool calls' }).last()).toContainText(
      'what-if · case 17 · has_guarantor=true',
    )
    await expect(rows.nth(0)).toContainText(question('Q-01').question)
    await expect(rows.nth(0)).toContainText('2 questions')
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

// the spec, section 08: a chip opens its target, which flashes once; the case is the one this session's run decided
test('opens the trace of a cited case once the cases have run', async ({ page }) => {
  await serveTheSeededRuleSet(page)
  await serveASeededRun(page)
  await serveTheChat(page)
  await page.goto('/')
  await page.getByLabel('Access code').fill('qwertyui')
  await page.getByRole('button', { name: 'Enter' }).click()
  const screens = page.getByRole('navigation', { name: 'Workspace' })
  await screens.getByRole('button', { name: /^Cases/ }).click()
  await page.getByRole('button', { name: 'Run 200 cases' }).first().click()
  await expect(page.getByRole('button', { name: '17', exact: true })).toBeVisible()
  await screens.getByRole('button', { name: 'Assistant' }).click()
  await ask(page, question('Q-01').question)

  const answer = page.locator('p[dir="rtl"][lang="he"]').filter({ hasText: 'ערב' })
  await answer.getByRole('button', { name: 'Case 17' }).click()

  await expect(page.getByRole('heading', { level: 1, name: 'Cases' })).toBeVisible()
  await expect(page.getByRole('complementary', { name: 'Case 17' })).toBeVisible()
  await expect(
    page.getByRole('row').filter({ has: page.getByRole('button', { name: '17', exact: true }) }),
  ).toHaveClass(/flash/)
})
