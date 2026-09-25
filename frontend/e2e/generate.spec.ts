import { expect, test } from '@playwright/test'
import {
  DRAFT_RULESET_ID,
  draftRuleSet,
  POLICY_ID,
  RULESET_ID,
  ruleSet,
  serveASeededRun,
  serveTheSeededRuleSet,
} from './seeded'

// @requirement FR-2
// @requirement FR-5

/**
 * Demo step 1 in a real browser, complete with the reviewer's two warnings (Document 1, Demo script; Work Plan days 7
 * and 10). The generation stream is answered by the test: the point here is that the analyst sees the stages in order,
 * then a draft, then what the reviewer found, not that a model was called.
 */

const STAGES =
  'event:parsing\ndata:{"paragraphs":9}\n\n' +
  'event:authoring\ndata:{"paragraphs":9}\n\n' +
  'event:validating\ndata:{"paragraphs":9}\n\n' +
  'event:reviewing\ndata:{"paragraphs":9}\n\n'

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

/**
 * The review of step 1: SF-1 and SF-2 of fixtures/eval/policies/consumer-lending/seeded.findings.json, the undefined
 * "stable income" and the age conflict of paragraphs 1 and 8 (Document 1, demo step 1: "two rows carry warnings").
 */
const REVIEW = {
  status: 'DONE',
  promptVersion: 'v1',
  coverage: {},
  findings: [
    {
      id: 'F-1',
      kind: 'ambiguity',
      severity: 'warning',
      ruleIds: ['R-420'],
      paragraphIndexes: [4],
      message: 'הכנסה יציבה אינה מוגדרת',
      suggestion: 'להוסיף סימון לבדיקה ידנית',
      confidence: 0.8,
      blocking: false,
    },
    {
      id: 'F-2',
      kind: 'conflict',
      severity: 'error',
      ruleIds: ['R-110', 'R-115'],
      paragraphIndexes: [1, 8],
      message: 'סעיף 1 מגביל את הגיל ל-70 וסעיף 8 מתיר גמלאים עד 75',
      suggestion: 'להחריג גמלאים מ-R-110',
      confidence: 0.9,
      blocking: true,
    },
  ],
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

  // Work Plan day 10, Done when: "Step 1 shows the ambiguity and the conflict"
  test('shows the ambiguity and the conflict the reviewer found, on the rows they name', async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    const draft = {
      ...DRAFT,
      ruleSet,
      review: REVIEW,
      policyVersionId: '0f4c1c9e-0000-4000-8000-0000000000d1',
    }
    await page.route('**/api/v1/policies/*/rulesets', (route) =>
      route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
        body: STAGES + `event:draft\ndata:${JSON.stringify(draft)}\n\n`,
      }),
    )
    await page.route('**/api/v1/rulesets', (route) =>
      route.fulfill({
        json: {
          rulesets: [
            {
              id: DRAFT.rulesetId,
              name: DRAFT.name,
              domain: DRAFT.domain,
              protected: false,
              policyId: POLICY_ID,
              versions: [{ versionNo: 1, status: 'DRAFT' }],
            },
          ],
        },
      }),
    )
    await page.route(`**/api/v1/rulesets/${DRAFT.rulesetId}/versions/*`, (route) =>
      route.fulfill({ json: draft }),
    )
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await page.getByRole('navigation', { name: 'Workspace' }).waitFor()

    await page.getByRole('button', { name: 'Generate rules' }).click()

    await expect(
      page.getByText('The reviewer found 2 things to check against the policy:'),
    ).toBeVisible()
    await page.getByRole('button', { name: 'Review the draft' }).click()
    const table = page.getByRole('table')
    await expect(
      table.getByRole('row').filter({ hasText: 'R-110' }).getByText('Conflict'),
    ).toBeVisible()
    await expect(
      table.getByRole('row').filter({ hasText: 'R-420' }).getByText('Ambiguity'),
    ).toBeVisible()
    await expect(
      page.getByText('Publishing waits: 1 finding must be acknowledged: F-2.'),
    ).toBeVisible()
    await expect(page.getByRole('button', { name: 'Publish version' })).toBeDisabled()
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

  test('opens the draft it just wrote, not the rule set that was already there', async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    let generated = false
    const seeded = {
      id: RULESET_ID,
      name: 'מדיניות אשראי צרכני',
      domain: 'consumer-lending',
      protected: true,
      policyId: POLICY_ID,
      versions: [{ versionNo: 1, status: 'PUBLISHED' }],
    }
    const written = {
      id: DRAFT_RULESET_ID,
      name: draftRuleSet.name,
      domain: 'rental-deposit',
      protected: false,
      policyId: POLICY_ID,
      versions: [{ versionNo: 1, status: 'DRAFT' }],
    }
    // the list the API answers grows once the generation has run, exactly as it does in the cloud
    await page.route('**/api/v1/rulesets', (route) =>
      route.fulfill({ json: { rulesets: generated ? [seeded, written] : [seeded] } }),
    )
    await page.route(`**/api/v1/rulesets/${DRAFT_RULESET_ID}/versions/*`, (route) =>
      route.fulfill({
        json: {
          rulesetId: DRAFT_RULESET_ID,
          name: draftRuleSet.name,
          domain: 'rental-deposit',
          protected: false,
          versionId: '0f4c1c9e-0000-4000-8000-0000000000c2',
          versionNo: 1,
          status: 'DRAFT',
          policyVersionId: '0f4c1c9e-0000-4000-8000-0000000000d2',
          ruleSet: draftRuleSet,
          findings: [],
        },
      }),
    )
    await page.route('**/api/v1/policies/*/rulesets', (route) => {
      generated = true
      return route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
        body:
          STAGES +
          `event:draft\ndata:${JSON.stringify({
            ...DRAFT,
            rulesetId: DRAFT_RULESET_ID,
            name: draftRuleSet.name,
            domain: 'rental-deposit',
            ruleSet: draftRuleSet,
          })}\n\n`,
      })
    })
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await page.getByRole('navigation', { name: 'Workspace' }).waitFor()

    await page.getByRole('button', { name: 'Generate rules' }).click()
    await page.getByRole('button', { name: 'Review the draft' }).click()

    // the rules on the screen are the draft's own, and the seeded set is not what opened
    await expect(page.getByText(draftRuleSet.rules[0].label)).toBeVisible()
    await expect(page.getByText(ruleSet.rules[0].label)).toHaveCount(0)
  })

  // Demo steps 1 and 2 in the order they are shown: the draft is opened to show its warnings, then the 200 cases run.
  // Document 2, decide: only a published version decides, so they run on the seeded version and not on the draft
  test('runs the cases on the seeded version right after the draft was opened', async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    await serveASeededRun(page)
    const decided: string[] = []
    await page.route('**/api/v1/rulesets/*/versions/*/decide', (route) => {
      decided.push(new URL(route.request().url()).pathname)
      return route.fallback()
    })
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
            {
              id: DRAFT.rulesetId,
              name: DRAFT.name,
              domain: DRAFT.domain,
              protected: false,
              policyId: POLICY_ID,
              versions: [{ versionNo: 1, status: 'DRAFT' }],
            },
          ],
        },
      }),
    )
    const draft = { ...DRAFT, ruleSet, review: REVIEW }
    await page.route(`**/api/v1/rulesets/${DRAFT.rulesetId}/versions/*`, (route) =>
      route.fulfill({ json: draft }),
    )
    await page.route('**/api/v1/policies/*/rulesets', (route) =>
      route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
        body: STAGES + `event:draft\ndata:${JSON.stringify(draft)}\n\n`,
      }),
    )
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await page.getByRole('navigation', { name: 'Workspace' }).waitFor()
    await page.getByRole('button', { name: 'Generate rules' }).click()
    await page.getByRole('button', { name: 'Review the draft' }).click()
    await page.getByRole('region', { name: 'Review of the draft' }).waitFor()

    await page
      .getByRole('navigation', { name: 'Workspace' })
      .getByRole('button', { name: 'Cases' })
      .click()
    await page.getByRole('button', { name: 'Run 200 cases' }).first().click()

    await expect(page.getByRole('term').filter({ hasText: 'Approved' })).toBeVisible()
    expect(decided).toEqual([`/api/v1/rulesets/${RULESET_ID}/versions/1/decide`])
    await expect(
      page.getByText(
        'The rule set on the workspace has no published version yet; the cases run on the seeded one.',
      ),
    ).toBeVisible()
  })
})
