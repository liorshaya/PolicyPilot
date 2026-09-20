import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { expect, test, type Page } from '@playwright/test'
import type { RuleSetDocument } from '../src/api/types'

/** The rule set is read from the committed fixture, so the test and the demo show the same rules. */
const ruleSet = JSON.parse(
  readFileSync(
    fileURLToPath(
      new URL('../../fixtures/policies/consumer-lending/ruleset.v1.json', import.meta.url),
    ),
    'utf8',
  ),
) as RuleSetDocument

/**
 * Demo step 2 in a real browser (Document 1, Demo script; Document 6, End to end): the rule set opens as a decision
 * table, and choosing a rule shows the paragraph of the policy it was written from. The API is answered by the test,
 * with the committed fixture, because CI stage 7 runs without a backend.
 */

const RULESET_ID = '0f4c1c9e-0000-4000-8000-0000000000b1'
const POLICY_ID = '0f4c1c9e-0000-4000-8000-0000000000a1'

const paragraphs = [
  { index: 6, text: 'יחס החוב להכנסה לא יעלה על 40%.' },
  {
    index: 7,
    text: 'מבקש שנרשמו לו שני אירועי אשראי שליליים או יותר ב-24 החודשים האחרונים, בקשתו תידחה. מבקש עם אירוע אחד יידרש להעמיד ערב.',
  },
]

async function serveTheSeededRuleSet(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/code', (route) => route.fulfill({ status: 204 }))
  await page.route('**/api/v1/policies', (route) =>
    route.fulfill({
      json: {
        policies: [
          {
            id: POLICY_ID,
            title: 'מדיניות אשראי צרכני',
            language: 'he',
            protected: true,
            versionNo: 1,
            paragraphs: paragraphs.length,
            createdAt: '2026-09-20T09:00:00Z',
          },
        ],
      },
    }),
  )
  await page.route(`**/api/v1/policies/${POLICY_ID}`, (route) =>
    route.fulfill({
      json: {
        id: POLICY_ID,
        title: 'מדיניות אשראי צרכני',
        language: 'he',
        protected: true,
        createdAt: '2026-09-20T09:00:00Z',
        versions: [{ versionNo: 1, createdAt: '2026-09-20T09:00:00Z', paragraphs }],
      },
    }),
  )
  await page.route('**/api/v1/rulesets', (route) =>
    route.fulfill({
      json: {
        rulesets: [
          {
            id: RULESET_ID,
            name: 'מדיניות אשראי צרכני',
            domain: 'consumer-lending',
            protected: true,
            policyId: POLICY_ID,
            versions: [{ versionNo: 1, status: 'PUBLISHED' }],
          },
        ],
      },
    }),
  )
  await page.route('**/api/v1/rulesets/*/versions/*', (route) =>
    route.fulfill({
      json: {
        rulesetId: RULESET_ID,
        name: 'מדיניות אשראי צרכני',
        domain: 'consumer-lending',
        protected: true,
        versionId: '0f4c1c9e-0000-4000-8000-0000000000c1',
        versionNo: 1,
        status: 'PUBLISHED',
        publishedAt: '2026-09-20T09:00:00Z',
        publishedBy: 'demo-analyst',
        ruleSet,
        findings: [],
      },
    }),
  )
}

test.describe('the rule set screen', () => {
  test.beforeEach(async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await page.getByRole('navigation', { name: 'Workspace' }).waitFor()
  })

  test('opens the rule set as a decision table of the published version', async ({ page }) => {
    await page
      .getByRole('navigation', { name: 'Workspace' })
      .getByRole('button', { name: 'Rules' })
      .click()

    await expect(page.getByText('Version 1')).toBeVisible()
    await expect(page.getByText('Published', { exact: true })).toBeVisible()
    await expect(page.getByRole('row')).toHaveCount(ruleSet.rules.length + 1)
    // the decision a rule makes, in the words a person reads
    await expect(page.getByRole('cell', { name: 'Approved' })).toBeVisible()
    // a published version is read, never edited
    await expect(page.getByRole('textbox')).toHaveCount(0)
  })

  test('choosing a rule shows the paragraph it was written from', async ({ page }) => {
    await page
      .getByRole('navigation', { name: 'Workspace' })
      .getByRole('button', { name: 'Rules' })
      .click()

    await page.getByRole('button', { name: /R-330/ }).click()

    await expect(page.getByText('Paragraph 7 is the source of R-330')).toBeVisible()
    const cited = page.locator('#paragraph-7')
    await expect(cited).toHaveAttribute('aria-current', 'true')
    await expect(cited).toContainText('מבקש עם אירוע אחד יידרש להעמיד ערב')
  })

  test('shows the rule beside its source and the document the engine runs', async ({ page }) => {
    await page
      .getByRole('navigation', { name: 'Workspace' })
      .getByRole('button', { name: 'Rules' })
      .click()
    await page.getByRole('button', { name: /R-330/ }).click()

    await page.getByRole('button', { name: 'Rule', exact: true }).click()
    await expect(page.getByText('model confidence 0.88')).toBeVisible()

    await page.getByRole('button', { name: 'JSON' }).click()
    await expect(page.getByText('"dslVersion": "1.0"')).toBeVisible()
  })
})
