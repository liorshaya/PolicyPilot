import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'
import { POLICY_ID, ruleSet } from './seeded'

/**
 * The lending demo whole, as the register spec shows it (Document 9, phase 6): the policy's nine paragraphs as
 * policy.he.md holds them, the run of the 200 seeded cases as cases-expected.json decides them, and case 17 as
 * sample-decision.json holds it, the worked example of Document 3. Registered after serveTheSeededRuleSet and
 * serveASeededRun, whose two paragraphs and one case these answer over (Playwright tries the last route that matches).
 */

const fixture = (path: string): string =>
  readFileSync(fileURLToPath(new URL(`../../fixtures/${path}`, import.meta.url)), 'utf8')

/** The policy's paragraphs, a blank line between two, numbered from 1. */
export const wholePolicy = fixture('policies/consumer-lending/policy.he.md')
  .split('\n\n')
  .map((block) => block.trim())
  .filter((block) => block !== '')
  .map((text, position) => ({ index: position + 1, text }))

const expected = JSON.parse(fixture('policies/consumer-lending/cases-expected.json')) as {
  cases: { id: number; outcome: string; decidingRuleId: string; flags: string[] }[]
}
const sample = JSON.parse(fixture('policies/consumer-lending/sample-decision.json')) as Record<
  string,
  unknown
>

/** A decision id per case, readable in a failure message: case 8 is ...000000000008. */
const decisionIdOf = (caseNo: number): string =>
  `0f4c1c9e-0000-4000-8000-${String(caseNo).padStart(12, '0')}`

/** The seeded policy with its nine paragraphs, named as its rule set names it. */
export async function serveTheWholePolicy(page: Page): Promise<void> {
  const createdAt = '2026-09-20T09:00:00Z'
  await page.route('**/api/v1/policies', (route) =>
    route.request().method() === 'GET'
      ? route.fulfill({
          json: {
            policies: [
              {
                id: POLICY_ID,
                title: ruleSet.name,
                language: 'he',
                protected: true,
                versionNo: 1,
                paragraphs: wholePolicy.length,
                createdAt,
              },
            ],
          },
        })
      : route.fallback(),
  )
  await page.route(`**/api/v1/policies/${POLICY_ID}`, (route) =>
    route.fulfill({
      json: {
        id: POLICY_ID,
        title: ruleSet.name,
        language: 'he',
        protected: true,
        createdAt,
        versions: [{ versionNo: 1, createdAt, paragraphs: wholePolicy }],
      },
    }),
  )
}

/**
 * The run of the 200 cases on the published version: the statistics empty until the run, then its counts and the five
 * rules that decided most; every case's line; case 17's decision, the one a test opens.
 */
export async function serveTheWholeRun(page: Page): Promise<void> {
  const outcomes: Record<string, number> = {}
  const deciding: Record<string, number> = {}
  for (const decided of expected.cases) {
    outcomes[decided.outcome] = (outcomes[decided.outcome] ?? 0) + 1
    deciding[decided.decidingRuleId] = (deciding[decided.decidingRuleId] ?? 0) + 1
  }
  const aggregates = {
    outcomes,
    errors: 0,
    decisions: expected.cases.length,
    topDecidingRules: Object.entries(deciding)
      .sort(([one, many], [other, more]) => more - many || one.localeCompare(other))
      .slice(0, 5)
      .map(([ruleId, count]) => ({ ruleId, count })),
  }
  const results = expected.cases.map((decided) => ({
    id: decisionIdOf(decided.id),
    caseNo: decided.id,
    status: 'OK',
    outcome: decided.outcome,
    decidingRuleId: decided.decidingRuleId,
    flags: decided.flags,
  }))
  let ran = false
  await page.route('**/api/v1/rulesets/*/versions/*/stats', (route) =>
    route.fulfill({
      json: ran ? aggregates : { outcomes: {}, errors: 0, topDecidingRules: [], decisions: 0 },
    }),
  )
  await page.route('**/api/v1/rulesets/*/versions/*/decide', (route) => {
    ran = true
    return route.fulfill({ json: { aggregates, results } })
  })
  await page.route(`**/api/v1/decisions/${decisionIdOf(17)}`, (route) =>
    route.fulfill({
      json: {
        ...sample,
        id: decisionIdOf(17),
        caseNo: 17,
        rulesetVersion: {
          id: 'consumer-lending',
          versionNo: 1,
          versionId: '0f4c1c9e-0000-4000-8000-0000000000c1',
        },
        decidedAt: '2026-09-23T10:14:07Z',
        durationMicros: 61,
      },
    }),
  )
}
