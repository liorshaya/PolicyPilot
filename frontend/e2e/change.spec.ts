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

    // the refusal block of the spec's section 08: the code, a row per refused patch, and the closing fact
    const refusal = page.getByRole('alert')
    await expect(refusal.locator('.refusal__head')).toHaveText(
      'RULESET_INVALIDThe proposal was refused.',
    )
    // every rule of version 1 that rejects, R-170 aside, and the defaults: 11 removes and 1 set_defaults refused
    await expect(refusal.getByRole('term')).toHaveCount(12)
    await expect(refusal.locator('.refusal__foot')).toContainText('Nothing was stored.')

    // what the model attempted stays behind a link until the analyst asks for it
    await refusal.getByRole('button', { name: 'What the model proposed' }).click()

    const attempted = page.getByRole('list', { name: 'What the model proposed' })
    await expect(attempted).toContainText('Remove R-100')
    await expect(attempted.getByRole('listitem').last()).toHaveText('Set the defaults to Approved')
    await expect(page.getByRole('button', { name: /^Approve and publish/ })).toHaveCount(0)
  })
})
