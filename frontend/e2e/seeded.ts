import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'
import type { RuleSetDocument } from '../src/api/types'

/** The rule set is read from the committed fixture, so the test and the demo show the same rules. */
export const ruleSet = JSON.parse(
  readFileSync(
    fileURLToPath(
      new URL('../../fixtures/policies/consumer-lending/ruleset.v1.json', import.meta.url),
    ),
    'utf8',
  ),
) as RuleSetDocument

/**
 * The seeded policy, rule set and decisions as the tests serve them: CI stage 7 runs these flows without a backend,
 * every route the page calls answered here from the committed fixtures. The rule set is the committed fixture, so the
 * browser shows the rules the demo shows.
 */

export const RULESET_ID = '0f4c1c9e-0000-4000-8000-0000000000b1'
export const POLICY_ID = '0f4c1c9e-0000-4000-8000-0000000000a1'

/** A second rule set, so a test can tell "the one just written" from "the one that was already there". */
export const DRAFT_RULESET_ID = '0f4c1c9e-0000-4000-8000-0000000000b2'

export const draftRuleSet = JSON.parse(
  readFileSync(
    fileURLToPath(
      new URL(
        '../../fixtures/eval/policies/rental-deposit-en/expected.ruleset.json',
        import.meta.url,
      ),
    ),
    'utf8',
  ),
) as RuleSetDocument

export const paragraphs = [
  { index: 6, text: 'יחס החוב להכנסה לא יעלה על 40%.' },
  {
    index: 7,
    text: 'מבקש שנרשמו לו שני אירועי אשראי שליליים או יותר ב-24 החודשים האחרונים, בקשתו תידחה. מבקש עם אירוע אחד יידרש להעמיד ערב.',
  },
]

/** The seeded rule set as GET /rulesets lists it: protected, its one version published. */
export const seededRuleset = {
  id: RULESET_ID,
  name: 'מדיניות אשראי צרכני',
  domain: 'consumer-lending',
  protected: true,
  policyId: POLICY_ID,
  versions: [{ versionNo: 1, status: 'PUBLISHED' }],
}

export async function serveTheSeededRuleSet(page: Page): Promise<void> {
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
    route.fulfill({ json: { rulesets: [seededRuleset] } }),
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

/** The aggregates and the two decisions the case runner is shown with; the counts are those of the seeded set. */
export async function serveASeededRun(page: Page): Promise<void> {
  const decisionId = '0f4c1c9e-0000-4000-8000-0000000000e1'
  await page.route('**/api/v1/rulesets/*/versions/*/stats', (route) =>
    route.fulfill({ json: { outcomes: {}, errors: 0, topDecidingRules: [], decisions: 0 } }),
  )
  await page.route('**/api/v1/rulesets/*/versions/*/decide', (route) =>
    route.fulfill({
      json: {
        aggregates: {
          outcomes: { approve: 113, reject: 60, refer: 27 },
          errors: 0,
          topDecidingRules: [
            { ruleId: 'R-900', count: 113 },
            { ruleId: 'R-330', count: 11 },
          ],
          decisions: 200,
        },
        results: [
          {
            id: decisionId,
            caseNo: 17,
            status: 'OK',
            outcome: 'refer',
            decidingRuleId: 'R-330',
            flags: [],
          },
        ],
      },
    }),
  )
  await page.route(`**/api/v1/decisions/${decisionId}`, (route) =>
    route.fulfill({
      json: {
        status: 'OK',
        outcome: 'refer',
        reason: 'נדרש ערב בשל אירוע אשראי אחד ב-24 החודשים האחרונים',
        decidingRuleId: 'R-330',
        terminal: true,
        derived: { debt_to_income: 0.2835 },
        flags: [],
        candidates: [],
        trace: [
          {
            ruleId: 'R-170',
            label: 'דחייה: הכנסה חודשית נטו נמוכה מ-8,000',
            priority: 170,
            status: 'not_fired',
            comparisons: [
              { field: 'monthly_income', op: 'lt', expected: 8000, actual: 9500, result: false },
            ],
          },
          {
            ruleId: 'R-330',
            label: 'בדיקת חתם: אירוע אשראי אחד ללא ערב',
            priority: 330,
            status: 'fired',
            comparisons: [
              { field: 'credit_events_24m', op: 'eq', expected: 1, actual: 1, result: true },
            ],
            actions: [{ type: 'decide', outcome: 'refer', terminal: true }],
          },
        ],
        id: decisionId,
        caseNo: 17,
        rulesetVersion: {
          id: 'consumer-lending',
          versionNo: 1,
          versionId: '0f4c1c9e-0000-4000-8000-0000000000c1',
        },
        decidedAt: '2026-09-20T09:05:00Z',
        durationMicros: 412,
      },
    }),
  )
}
