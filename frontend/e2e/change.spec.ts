import { expect, test } from '@playwright/test'
import { RT_04_TEXT, serveTheChange } from './change'
import { serveTheSeededRuleSet } from './seeded'

// @requirement FR-17

/**
 * The change screen in a real browser, typed by hand rather than through the panel (Document 5, RT-04: "the stream ends
 * with the refusal and the model's answer, so the analyst sees what was attempted, and nothing is stored"; Work Plan
 * day 14: RT-04's rejection visible to the analyst through the screen).
 */
test.describe('the change screen', () => {
  test.beforeEach(async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await serveTheChange(page)
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await page
      .getByRole('navigation', { name: 'Workspace' })
      .getByRole('button', { name: /^Change/ })
      .click()
  })

  test('RT-04: the refusal and what the model attempted reach the analyst, and nothing can be approved', async ({
    page,
  }) => {
    await page.getByLabel('What should change').fill(RT_04_TEXT)
    await page.getByRole('button', { name: 'Propose the change' }).click()

    const refusal = page.getByRole('alert')
    await expect(refusal).toContainText(
      'The proposal was refused (RULESET_INVALID). Nothing was stored.',
    )
    // every rule of version 1 that rejects, R-170 aside, and the defaults: 11 removes and 1 set_defaults refused
    await expect(
      refusal.getByRole('list', { name: 'Why it was refused' }).getByRole('listitem'),
    ).toHaveCount(12)
    const attempted = refusal.getByRole('list', { name: 'What the model proposed' })
    await expect(attempted).toContainText('Remove R-100')
    await expect(attempted.getByRole('listitem').last()).toHaveText('Set the defaults to Approved')
    await expect(page.getByRole('button', { name: 'Approve and publish' })).toHaveCount(0)
  })
})
