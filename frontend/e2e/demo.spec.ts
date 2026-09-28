import { expect, test, type Page } from '@playwright/test'
import { casesExpected, changeRequest, serveTheChange } from './change'
import { ask, notCovered, question, serveTheChat } from './chat'
import { step } from './panel'
import { paragraphs, serveASeededRun, serveTheSeededRuleSet } from './seeded'
import { serveTheSession } from './session'

// @requirement FR-21
// @requirement FR-23
// @requirement NFR-5

/**
 * The presenter's run (Brief FR-23 and the demo script; Work Plan day 16: "all four steps through the guided panel in
 * one run"): one page, from the access gate to version 2 in the audit log, each step started from the guided panel the
 * way the demo is driven from an interviewer's machine. What each step shows is proved on its own in generate.spec.ts,
 * cases.spec.ts, chat.spec.ts and change.spec.ts; this run proves that the steps follow one another in one session,
 * each starting from the workspace the step before it left. Every API call is answered from the committed fixtures,
 * as in CI stage 7 (Document 6).
 */

const CODE = 'qwertyui'
const NOTE = 'אושר בוועדת האשראי'

/** The gate against the API's two answers (Document 2, POST /auth/code): 204 for the code, 401 for anything else. */
async function serveTheGate(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/code', (route) => {
    const { code } = route.request().postDataJSON() as { code: string }
    return code === CODE
      ? route.fulfill({ status: 204 })
      : route.fulfill({ status: 401, json: { code: 'ACCESS_CODE_INVALID' } })
  })
}

test('the presenter runs the gate, then steps 1 to 4 through the guided panel, in one page', async ({
  page,
}) => {
  test.setTimeout(90_000)
  await serveTheSeededRuleSet(page)
  await serveASeededRun(page)
  await serveTheChat(page)
  await serveTheChange(page)
  const session = await serveTheSession(page, paragraphs)
  await serveTheGate(page)

  // The gate: a wrong code is refused on the gate, the code opens the workspace, and the rail names the provider
  await page.goto('/')
  await page.getByLabel('Access code').fill('wrongone')
  await page.getByRole('button', { name: 'Enter' }).click()
  await expect(page.getByRole('alert')).toHaveText('That code is not valid.')
  await page.getByLabel('Access code').fill(CODE)
  await page.getByRole('button', { name: 'Enter' }).click()
  const workspace = page.getByRole('navigation', { name: 'Workspace' })
  await expect(workspace).toBeVisible()
  await expect(workspace.getByText('Provider OpenAI · cloud')).toBeVisible()
  await page.getByRole('button', { name: /guided demo/i }).click()

  // Step 1, Author: the sample policy pasted and added, its rules written, the reviewer's findings on their rows
  await step(page, 'Author').click()
  await expect(page.getByLabel('Policy text')).toHaveValue(
    paragraphs.map((paragraph) => paragraph.text).join('\n\n'),
  )
  await page.getByRole('button', { name: 'Add policy' }).click()
  await page.getByRole('button', { name: 'Generate rules' }).click()
  await expect(page.getByText('A draft rule set was written from this policy:')).toBeVisible()
  await expect(
    page.getByRole('region', { name: 'The reviewer found 2 things to check' }),
  ).toBeVisible()
  // the pasted copy is generated with the seeded rule set's inputs as its field hints (Document 4, Field hints)
  expect(session.generatedWith()).toMatchObject({
    hints: expect.stringContaining('monthly_income'),
  })
  await page.getByRole('button', { name: 'Review the draft' }).click()
  const table = page.getByRole('table')
  await expect(
    table.getByRole('row').filter({ hasText: 'R-110' }).getByText('Conflict'),
  ).toBeVisible()
  await expect(
    table.getByRole('row').filter({ hasText: 'R-420' }).getByText('Ambiguity'),
  ).toBeVisible()

  // Step 2, Decide: the 200 seeded cases decided on the published version, then case 17's trace
  await step(page, 'Decide').click()
  await expect(page.getByRole('heading', { level: 1, name: 'Cases' })).toBeVisible()
  // 113 of the 200 are approved (fixtures/policies/consumer-lending/cases-expected.json)
  await expect(page.getByRole('definition').filter({ hasText: '56.5%' })).toContainText('113')
  await page.getByRole('button', { name: '17', exact: true }).click()
  await expect(page.getByRole('complementary').getByRole('listitem').last()).toContainText(
    'Matched',
  )

  // Step 3, Ask: the three scripted questions, then the rate question the documents do not cover
  await step(page, 'Ask').click()
  await expect(page.getByLabel('Question', { exact: true })).toHaveValue(question('Q-01').question)
  await page.getByRole('button', { name: 'Ask' }).click()
  await expect(
    page.getByRole('group', { name: 'Sources' }).getByRole('button', { name: 'Case 17' }),
  ).toBeVisible()
  await ask(page, question('Q-02').question)
  await expect(page.getByRole('list', { name: 'Tool calls' }).last()).toContainText(
    'what-if · case 17 · has_guarantor=true',
  )
  await ask(page, question('Q-03').question)
  await expect(
    page
      .getByRole('group', { name: 'Sources' })
      .last()
      .getByRole('button', { name: 'Paragraph 2' }),
  ).toBeVisible()
  await ask(page, question('Q-04').question)
  // the Hebrew answers are laid out right to left inside the English chrome (NFR-5)
  await expect(
    page.locator('p[dir="rtl"][lang="he"]').filter({ hasText: notCovered('he') }),
  ).toBeVisible()

  // Step 4, Change: the scripted request, its 12 flips, the approval, and version 2 with its audit entry
  await step(page, 'Change').click()
  await expect(page.getByLabel('What should change')).toHaveValue(changeRequest.text.he)
  await page.getByRole('button', { name: 'Propose the change' }).click()
  const report = page.getByRole('region', { name: 'Regression report' })
  await expect(report.locator('.figure').first()).toContainText('126.0% of 200')
  await report.getByRole('button', { name: 'Show all 12' }).click()
  await expect(
    report
      .getByRole('table', { name: 'The decisions that flip' })
      .locator('tbody tr td:first-child'),
  ).toHaveText(casesExpected.regression.flips.map((flip) => String(flip.id)))
  await page.getByLabel('Note for the audit log').fill(NOTE)
  await page.getByRole('button', { name: 'Approve and publish v2' }).click()
  await expect(page.getByText('Version 2', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Open the audit log' }).click()
  const entry = page.getByRole('region', { name: 'Audit log' }).getByRole('listitem').first()
  await expect(entry.locator('.event__verb')).toHaveText('Change approved')
  await expect(entry).toContainText(NOTE)
})
