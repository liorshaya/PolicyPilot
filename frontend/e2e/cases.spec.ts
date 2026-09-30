import { expect, test } from '@playwright/test'
import { serveASeededRun, serveTheSeededRuleSet } from './seeded'

/**
 * Demo step 3 in a real browser (Document 1, Demo script; Document 6, End to end): the 200 seeded cases are run,
 * the outcomes are summed up, and one case opens the trace the engine wrote for it.
 */

test.describe('the case runner', () => {
  test.beforeEach(async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await serveASeededRun(page)
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await page
      .getByRole('navigation', { name: 'Workspace' })
      .getByRole('button', { name: 'Cases' })
      .click()
  })

  test('runs the seeded set and sums up what the engine decided', async ({ page }) => {
    await expect(page.getByText('Nothing decided yet.', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: 'Run 200 cases' }).first().click()

    // 113 of the 200 seeded cases are approved (fixtures/policies/consumer-lending/cases-expected.json)
    await expect(page.getByRole('term').filter({ hasText: 'Approved' })).toBeVisible()
    await expect(page.getByRole('definition').filter({ hasText: '56.5%' })).toContainText('113')
    await expect(page.getByRole('heading', { level: 2, name: 'Decisions on v1' })).toBeVisible()
    await expect(
      page.getByText('Rules that decided most often · click to filter the list'),
    ).toBeVisible()
  })

  test('opens the trace of a case, step by step', async ({ page }) => {
    await page.getByRole('button', { name: 'Run 200 cases' }).first().click()

    await page.getByRole('button', { name: '17', exact: true }).click()

    const trace = page.getByRole('complementary')
    await expect(trace.getByText('engine · 412 µs')).toBeVisible()
    const steps = trace.getByRole('listitem')
    await expect(steps).toHaveCount(2)
    await expect(steps.first()).toContainText('Did not match')
    await expect(steps.last()).toContainText('Matched · decided')
    // a rule that did not match shows its head until every comparison is shown (phase 3's sixth question)
    await expect(steps.first()).not.toContainText('9,500')
    await trace.getByRole('button', { name: 'Show every comparison' }).click()
    await expect(steps.first()).toContainText('9,500')
  })

  test('names the rule that decided each case, and clears its filters in one click', async ({
    page,
  }) => {
    await page.getByRole('button', { name: 'Run 200 cases' }).first().click()

    // the spec, section 07 (v3.7): the label under the id, from the version the run decided on
    const row = page.getByRole('row').filter({ hasText: 'R-330' })
    await expect(row).toContainText('בדיקת חתם: אירוע אשראי אחד ללא ערב')
    await page.getByRole('combobox', { name: 'Outcome' }).selectOption('reject')
    await expect(page.getByText('Cases 0 of 1 · one run on v1')).toBeVisible()

    await page.getByRole('button', { name: 'Clear filters' }).click()

    await expect(page.getByText('Cases 1–1 of 1 · one run on v1')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Clear filters' })).toHaveCount(0)
  })

  // Work Plan day 10, Done when: "Explain on case 17 cites R-330 and its paragraph" (paragraph 7, R-330's provenance)
  test('explains case 17 by R-330 and its paragraph when the reader asks', async ({ page }) => {
    let asked: unknown = null
    await page.route('**/api/v1/decisions/*/explain', (route) => {
      asked = route.request().postDataJSON()
      return route.fulfill({
        json: {
          decisionId: '0f4c1c9e-0000-4000-8000-0000000000e1',
          audience: 'officer',
          language: 'he',
          promptVersion: 'v1',
          summary:
            'הבקשה הופנתה לבדיקת חתם לפי R-330, משום שנרשם אירוע אשראי אחד ב-24 החודשים האחרונים ואין ערב.',
          factors: [{ ruleId: 'R-330', paragraph: 7, statement: 'נמצא אירוע אשראי אחד ואין ערב.' }],
          conditions: [],
          notApplied: [],
        },
      })
    })
    await page.getByRole('button', { name: 'Run 200 cases' }).first().click()
    await page.getByRole('button', { name: '17', exact: true }).click()

    await page.getByRole('button', { name: 'Explain for an officer' }).click()

    const explanation = page.getByRole('region', { name: 'Explanation' })
    await expect(explanation.getByRole('button', { name: 'R-330' })).toBeVisible()
    await expect(explanation.getByText('¶ 7')).toBeVisible()
    await expect(explanation.getByText(/Written by a model from this trace alone/)).toBeVisible()
    expect(asked).toEqual({ audience: 'officer' })
  })
})
