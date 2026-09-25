import { expect, test, type Page } from '@playwright/test'
import { casesExpected, changeRequest, sandboxCopy, serveTheChange } from './change'
import { ask, notCovered, question, serveTheChat } from './chat'
import { DRAFT, REVIEW, STAGES } from './generation'
import { step } from './panel'
import {
  paragraphs,
  ruleSet,
  seededRuleset,
  serveASeededRun,
  serveTheSeededRuleSet,
} from './seeded'

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

/** The copy step 1 pastes: the seeded policy's own text, in a policy of the visitor's sandbox. */
const PASTED_ID = '0f4c1c9e-0000-4000-8000-0000000000a3'
const pasted = {
  id: PASTED_ID,
  title: seededRuleset.name,
  language: 'he',
  protected: false,
  createdAt: '2026-09-28T09:00:00Z',
  versions: [{ versionNo: 1, createdAt: '2026-09-28T09:00:00Z', paragraphs }],
}

/** The draft step 1 writes from the pasted copy, with the reviewer's two findings. */
const draft = {
  ...DRAFT,
  ruleSet,
  review: REVIEW,
  policyVersionId: '0f4c1c9e-0000-4000-8000-0000000000d3',
}

/** The draft as the sandbox's list of rule sets names it once it is written. */
const written = {
  id: DRAFT.rulesetId,
  name: DRAFT.name,
  domain: DRAFT.domain,
  protected: false,
  policyId: PASTED_ID,
  versions: [{ versionNo: 1, status: 'DRAFT' }],
}

/** The gate against the API's two answers (Document 2, POST /auth/code): 204 for the code, 401 for anything else. */
async function serveTheGate(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/code', (route) => {
    const { code } = route.request().postDataJSON() as { code: string }
    return code === CODE
      ? route.fulfill({ status: 204 })
      : route.fulfill({ status: 401, json: { code: 'ACCESS_CODE_INVALID' } })
  })
}

/**
 * The session's own answers: the pasted copy of step 1 is created and read back, its generation streams the draft, and
 * the sandbox's list of rule sets grows with the draft, then with its copy of the seeded rule set once step 4 is
 * approved. Registered after the steps' routes, so these answer first (Playwright tries the last route that matches);
 * each flag is set inside a route, before the browser has the answer that makes it refetch the list.
 */
async function serveTheSession(page: Page): Promise<{ generatedWith: () => unknown }> {
  let generated: unknown = null
  let approved = false
  await page.route('**/api/v1/policies', (route) =>
    route.request().method() === 'POST'
      ? route.fulfill({ status: 201, json: pasted })
      : route.fallback(),
  )
  await page.route(`**/api/v1/policies/${PASTED_ID}`, (route) => route.fulfill({ json: pasted }))
  await page.route(`**/api/v1/policies/${PASTED_ID}/rulesets`, (route) => {
    generated = route.request().postDataJSON()
    return route.fulfill({
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
      body: STAGES + `event:draft\ndata:${JSON.stringify(draft)}\n\n`,
    })
  })
  await page.route(`**/api/v1/rulesets/${DRAFT.rulesetId}/versions/*`, (route) =>
    route.fulfill({ json: draft }),
  )
  await page.route('**/api/v1/changes/*/approve', (route) => {
    approved = true
    return route.fallback()
  })
  await page.route('**/api/v1/rulesets', (route) =>
    route.fulfill({
      json: {
        rulesets: [
          seededRuleset,
          ...(generated === null ? [] : [written]),
          ...(approved ? [sandboxCopy] : []),
        ],
      },
    }),
  )
  return { generatedWith: () => generated }
}

test('the presenter runs the gate, then steps 1 to 4 through the guided panel, in one page', async ({
  page,
}) => {
  test.setTimeout(90_000)
  await serveTheSeededRuleSet(page)
  await serveASeededRun(page)
  await serveTheChat(page)
  await serveTheChange(page)
  const session = await serveTheSession(page)
  await serveTheGate(page)

  // The gate: a wrong code is refused on the gate, the code opens the workspace, and the header names the provider
  await page.goto('/')
  await page.getByLabel('Access code').fill('wrongone')
  await page.getByRole('button', { name: 'Enter' }).click()
  await expect(page.getByRole('alert')).toHaveText('That code is not valid.')
  await page.getByLabel('Access code').fill(CODE)
  await page.getByRole('button', { name: 'Enter' }).click()
  const workspace = page.getByRole('navigation', { name: 'Workspace' })
  await expect(workspace).toBeVisible()
  await expect(workspace.getByRole('region', { name: 'Model provider' })).toContainText('OpenAI')
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
    page.getByText('The reviewer found 2 things to check against the policy:'),
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
  await expect(page.getByRole('definition').filter({ hasText: '57%' })).toContainText('113')
  await page.getByRole('button', { name: '17', exact: true }).click()
  await expect(page.getByRole('complementary').getByRole('listitem').last()).toContainText(
    'Matched',
  )

  // Step 3, Ask: the three scripted questions, then the rate question the documents do not cover
  await step(page, 'Ask').click()
  await expect(page.getByLabel('Question')).toHaveValue(question('Q-01').question)
  await page.getByRole('button', { name: 'Ask' }).click()
  await expect(page.getByRole('button', { name: 'Application 17' })).toBeVisible()
  await ask(page, question('Q-02').question)
  await expect(page.getByText('What if has_guarantor=true', { exact: true })).toBeVisible()
  await ask(page, question('Q-03').question)
  await expect(page.getByRole('button', { name: '¶ 2' })).toBeVisible()
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
  await expect(report).toContainText('12 of the 200 decisions made on version 1 flip.')
  await expect(report.locator('tbody tr td:first-child')).toHaveText(
    casesExpected.regression.flips.map((flip) => String(flip.id)),
  )
  await page.getByLabel('Note for the audit log').fill(NOTE)
  await page.getByRole('button', { name: 'Approve and publish' }).click()
  await expect(page.getByText('Version 2', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Open the audit log' }).click()
  const entry = page.getByRole('list', { name: 'Entries' }).getByRole('article').first()
  await expect(entry.getByRole('heading')).toHaveText('Change approved')
  await expect(entry).toContainText(NOTE)
})
