import { expect, test } from '@playwright/test'
import { ruleSet, serveTheSeededRuleSet } from './seeded'

/**
 * Demo step 1 in a real browser, without the reviewer flags that arrive on day 10 (Document 1, Demo script; Work
 * Plan day 7). The generation stream is answered by the test: the point here is that the analyst sees the stages
 * in order and then a draft, not that a model was called.
 */

const STAGES =
  'event:parsing\ndata:{"paragraphs":9}\n\n' +
  'event:authoring\ndata:{"paragraphs":9}\n\n' +
  'event:validating\ndata:{"paragraphs":9}\n\n'

const DRAFT = {
  rulesetId: '0f4c1c9e-0000-4000-8000-0000000000b9',
  name: 'מדיניות אשראי צרכני',
  domain: 'consumer-lending',
  protected: false,
  versionId: '0f4c1c9e-0000-4000-8000-0000000000c9',
  versionNo: 1,
  status: 'DRAFT',
  findings: [],
}

const REFUSAL =
  'event:error\ndata:{"code":"RULESET_INVALID","findings":[{"code":"PROVENANCE_QUOTE_MISMATCH",' +
  '"severity":"error","path":"/rules/0/provenance/quote","message":"R-100: the quote does not occur in ' +
  'paragraph 1","ruleIds":["R-100"],"fieldNames":[]}]}\n\n'

test.describe('demo step 1: the rules are written from the policy', () => {
  test('shows every stage and then the draft', async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await page.route('**/api/v1/policies/*/rulesets', (route) =>
      route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
        body: STAGES + `event:draft\ndata:${JSON.stringify({ ...DRAFT, ruleSet })}\n\n`,
      }),
    )
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await page.getByRole('navigation', { name: 'Workspace' }).waitFor()

    await page.getByRole('button', { name: 'Generate rules' }).click()

    await expect(page.getByText('A draft rule set was written from this policy:')).toBeVisible()
    await expect(page.getByText(`${String(ruleSet.rules.length)} rules`)).toBeVisible()
    // the engine decides, and nothing decides anything until a person publishes this draft
    await expect(page.getByText('Nothing decides cases until a person publishes it.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Review the draft' })).toBeVisible()
  })

  test('says what was refused and that nothing was stored', async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await page.route('**/api/v1/policies/*/rulesets', (route) =>
      route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
        body: STAGES + REFUSAL,
      }),
    )
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await page.getByRole('navigation', { name: 'Workspace' }).waitFor()

    await page.getByRole('button', { name: 'Generate rules' }).click()

    await expect(page.getByRole('alert')).toContainText('RULESET_INVALID')
    await expect(page.getByRole('alert')).toContainText('Nothing was stored')
    await expect(page.getByText('PROVENANCE_QUOTE_MISMATCH')).toBeVisible()
  })
})
