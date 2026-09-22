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
    await expect(page.getByText('Nothing decided yet')).toBeVisible()

    await page.getByRole('button', { name: 'Run 200 cases' }).first().click()

    // 113 of the 200 seeded cases are approved (fixtures/policies/consumer-lending/cases-expected.json)
    await expect(page.getByRole('term').filter({ hasText: 'Approved' })).toBeVisible()
    await expect(page.getByRole('definition').filter({ hasText: '57%' })).toContainText('113')
    await expect(page.getByText('Rules that decided most often, of 200 decisions')).toBeVisible()
  })

  test('opens the trace of a case, step by step', async ({ page }) => {
    await page.getByRole('button', { name: 'Run 200 cases' }).first().click()

    await page.getByRole('button', { name: '17', exact: true }).click()

    await expect(page.getByText('Decided in 412 µs by the engine')).toBeVisible()
    const steps = page.getByRole('complementary').getByRole('listitem')
    await expect(steps).toHaveCount(2)
    await expect(steps.first()).toContainText('Did not match')
    await expect(steps.first()).toContainText('9,500')
    await expect(steps.last()).toContainText('Matched')
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
