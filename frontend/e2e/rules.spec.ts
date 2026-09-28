import { expect, test } from '@playwright/test'
import { ruleSet, serveTheSeededRuleSet } from './seeded'

/**
 * Demo step 2 in a real browser (Document 1, Demo script; Document 6, End to end): the rule set opens as a decision
 * table, and choosing a rule shows the paragraph of the policy it was written from.
 */

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

    // the header names the version's state and number, "Published v1", and so does its primary: "Publish version 1"
    // (the spec, section 10)
    await expect(
      page.getByRole('heading', { level: 1 }).getByText('Published v1', { exact: true }),
    ).toBeVisible()
    await expect(page.getByRole('button', { name: 'Publish version 1' })).toBeDisabled()
    // one row per rule, plus the head and the band of Document 3 each group sits under
    await expect(page.getByRole('rowheader')).toHaveCount(ruleSet.rules.length)
    await expect(page.getByRole('columnheader', { name: 'Referral conditions' })).toBeVisible()
    // the decision a rule makes, in the words of a rule's action column (the spec's glossary)
    await expect(page.getByRole('cell', { name: 'Approve', exact: true })).toBeVisible()
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
    await expect(page.getByText('model · confidence 0.88')).toBeVisible()

    await page.getByRole('button', { name: 'JSON' }).click()
    await expect(page.getByText('"dslVersion": "1.0"')).toBeVisible()
  })
})
