import { expect, test, type Page } from '@playwright/test'
import { changeRequest, serveTheChange } from './change'
import { openThePanel } from './panel'
import { serveTheSeededRuleSet } from './seeded'

// @requirement FR-20
// @requirement FR-23

/**
 * The screens as the demo shows them, at the two widths it is shown at (Work Plan day 16, the pass over every screen in
 * both directions; days 17 and 18 rehearse from a laptop and from a phone). The pass found text nobody could read: the
 * guided panel's titles in the sidebar's white on the panel's own white and its descriptions a word a line, the change
 * screen squeezing its request out of sight once the proposal came, the side-by-side diff a letter a line on a phone,
 * the version pickers of the audit log cut off there, and the model names in the header broken at their hyphens. Each
 * test pins one of them where only a browser can see it, in the layout; the words themselves are the component tests'.
 */

const LAPTOP = { width: 1440, height: 900 }
const PHONE = { width: 390, height: 844 }

/** The colour of the workspace's text, --color-text (#142c43), as a browser reports it. */
const TEXT = 'rgb(20, 44, 67)'

async function enter(page: Page): Promise<void> {
  await page.goto('/')
  await page.getByLabel('Access code').fill('qwertyui')
  await page.getByRole('button', { name: 'Enter' }).click()
  await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()
}

async function proposeTheScriptedChange(page: Page): Promise<void> {
  await serveTheSeededRuleSet(page)
  await serveTheChange(page)
  await enter(page)
  await page
    .getByRole('navigation', { name: 'Workspace' })
    .getByRole('button', { name: /^Change/ })
    .click()
  await page.getByLabel('What should change').fill(changeRequest.text.he)
  await page.getByRole('button', { name: 'Propose the change' }).click()
  await expect(page.getByRole('region', { name: 'Regression report' })).toBeVisible()
}

test.describe('on a laptop', () => {
  test.use({ viewport: LAPTOP })

  test('the guided panel reads as a list of steps: dark titles on its white, each description a few words a line', async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    await openThePanel(page)

    const panel = page.getByRole('region', { name: 'Guided demo' })
    await expect(panel.getByText('Guided demo', { exact: true })).toHaveCSS('color', TEXT)
    await expect(panel.getByText('Author', { exact: true })).toHaveCSS('color', TEXT)
    const what = panel.getByText(
      'Fills the form with the sample lending policy, ready to generate its rules',
    )
    expect((await what.boundingBox())!.width).toBeGreaterThan(140)
  })

  test('the guided panel folds its steps away when hidden', async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await openThePanel(page)
    const panel = page.getByRole('region', { name: 'Guided demo' })
    await expect(panel.getByRole('list')).toBeVisible()

    await panel.getByRole('button', { name: /guided demo/i }).click()

    await expect(panel.getByRole('list')).toBeHidden()
  })

  test('the change screen keeps its request whole once the proposal is shown, and scrolls as one page', async ({
    page,
  }) => {
    await proposeTheScriptedChange(page)

    const request = page.getByLabel('What should change')
    await request.scrollIntoViewIfNeeded()
    await expect(request).toBeInViewport({ ratio: 1 })
  })

  test('a changed value is marked as one box, which begins with its first words', async ({
    page,
  }) => {
    await proposeTheScriptedChange(page)

    // R-170's action as proposed: the outcome and the reason, marked as inserted
    const inserted = page
      .getByRole('row')
      .filter({ hasText: 'R-170' })
      .locator('ins')
      .filter({ hasText: 'Decline' })
    const mark = (await inserted.boundingBox())!
    const first = (await inserted.getByText('Decline', { exact: true }).boundingBox())!
    // a mark that broke with its lines began with an empty line above its first word
    expect(first.y - mark.y).toBeLessThan(6)
  })

  test('the header names each model on a line of its own, never broken at its hyphens', async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    await enter(page)

    const badge = page.getByRole('region', { name: 'Model provider' })
    const edge = (await badge.boundingBox())!
    // one line is as tall as the one-word term "Provider" beside it
    const line = (await badge.getByText('Provider', { exact: true }).boundingBox())!.height
    for (const name of ['gpt-5.6-terra', 'gpt-5.6-luna', 'text-embedding-3-small']) {
      const model = (await badge.getByText(name, { exact: true }).boundingBox())!
      expect(model.height, name).toBeLessThan(line * 1.5)
      // and the name fits inside the badge rather than running past its edge
      expect(model.x + model.width, name).toBeLessThanOrEqual(edge.x + edge.width)
    }
  })
})

test.describe('on a phone', () => {
  test.use({ viewport: PHONE })

  test('the side-by-side diff keeps each value readable, words on a line rather than a letter', async ({
    page,
  }) => {
    await proposeTheScriptedChange(page)

    // the label of R-170 as proposed (change-request-1.json), the widest value of the diff's first row
    const replacement = changeRequest.expected.patches.find(
      (patch) => 'rule' in patch && patch.ruleId === 'R-170',
    )
    const proposed =
      replacement && 'rule' in replacement ? replacement.rule.label : 'no R-170 in the fixture'
    const label = page
      .getByRole('row')
      .filter({ hasText: 'R-170' })
      .getByText(proposed, { exact: true })
    expect((await label.boundingBox())!.width).toBeGreaterThan(150)
  })

  test("the audit log's version pickers stay on the screen", async ({ page }) => {
    await proposeTheScriptedChange(page)
    await page.getByLabel('Note for the audit log').fill('אושר בוועדת האשראי')
    await page.getByRole('button', { name: 'Approve and publish' }).click()
    await page.getByRole('button', { name: 'Open the audit log' }).click()

    const to = page.getByRole('combobox', { name: 'To' })
    await to.scrollIntoViewIfNeeded()
    await expect(to).toBeInViewport({ ratio: 1 })
  })
})
