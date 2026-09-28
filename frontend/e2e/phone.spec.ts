import { expect, test, type Page } from '@playwright/test'
import { changeRequest, serveTheChange } from './change'
import { serveTheChat } from './chat'
import { step } from './panel'
import { serveASeededRun, serveTheSeededRuleSet } from './seeded'

// @requirement FR-23

/**
 * The demo on a phone (the spec, section 10, "Cases at phone width"; Document 9, phase 5): at 390×844 the rail is the
 * top bar with the six screens as a row, the guided demo is in the menu, the four screens of the demo steps fit the
 * window with nothing scrolling sideways, and a row of the case list is a 44px touch target. Every API call is answered
 * from the committed fixtures, as in the other specs.
 */

test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })

async function enter(page: Page): Promise<void> {
  await serveTheSeededRuleSet(page)
  await serveASeededRun(page)
  await serveTheChat(page)
  await serveTheChange(page)
  await page.goto('/')
  await page.getByLabel('Access code').fill('qwertyui')
  await page.getByRole('button', { name: 'Enter' }).click()
  await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()
}

/** Opens a screen from the row of screens under the top bar. */
async function open(page: Page, name: string): Promise<void> {
  await page
    .getByRole('navigation', { name: 'Workspace' })
    .getByRole('button', { name: new RegExp(`^${name}`) })
    .click()
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()
}

/** The page's width against the window's: a phone never scrolls sideways (tables scroll in their own box). */
async function sideways(page: Page): Promise<number> {
  return page
    .locator('html')
    .evaluate(
      (root: { scrollWidth: number; clientWidth: number }) => root.scrollWidth - root.clientWidth,
    )
}

test.describe('the demo on a phone', () => {
  test.beforeEach(async ({ page }) => {
    await enter(page)
  })

  test("stands the top bar in the rail's place, with the six screens as a row", async ({
    page,
  }) => {
    await expect(page.locator('.rail')).toHaveCount(0)
    const top = page.locator('.phone__top')
    await expect(top.getByRole('img', { name: 'PolicyPilot' })).toBeVisible()
    await expect(top.getByRole('button', { name: 'Menu' })).toBeVisible()
    await expect(
      page.getByRole('navigation', { name: 'Workspace' }).getByRole('button'),
    ).toHaveCount(6)
  })

  test('fits the four screens of the demo steps to the window, nothing scrolling sideways', async ({
    page,
  }) => {
    for (const name of ['Policies', 'Cases', 'Assistant', 'Change']) {
      await open(page, name)
      expect(await sideways(page), name).toBeLessThanOrEqual(0)
    }
    // and the change screen once it holds a proposal, its diff and its regression (the cloud walk found the matrix
    // widening the page by 91px)
    await page.getByLabel('What should change').fill(changeRequest.text.he)
    await page.getByRole('button', { name: 'Propose the change' }).click()
    await expect(page.getByRole('region', { name: 'Regression report' })).toBeVisible()
    expect(await sideways(page), 'Change with a proposal').toBeLessThanOrEqual(0)
  })

  test('runs the steps of the guided demo from the menu, which closes on the screen each opens', async ({
    page,
  }) => {
    const menu = page.getByRole('dialog', { name: 'Menu' })
    // step 1 runs on Policies, the screen the workspace opens on
    await page.getByRole('button', { name: 'Menu' }).click()
    await menu.getByRole('button', { name: /guided demo/i }).click()
    await step(page, 'Author').click()
    await expect(menu).toHaveCount(0)
    await expect(page.getByLabel('Policy text')).not.toHaveValue('')

    await page.getByRole('button', { name: 'Menu' }).click()
    await step(page, 'Decide').click()
    await expect(page.getByRole('heading', { level: 1, name: 'Cases' })).toBeVisible()
    await expect(menu).toHaveCount(0)
    await expect(page.getByRole('button', { name: '17', exact: true })).toBeVisible()
  })

  test('makes a row of the case list a 44px touch target, and opens its trace under the list', async ({
    page,
  }) => {
    await open(page, 'Cases')
    await page.getByRole('button', { name: 'Run 200 cases' }).first().click()

    const row = page.getByRole('row').filter({ has: page.getByRole('button', { name: '17' }) })
    const box = await row.boundingBox()
    expect(box?.height).toBeGreaterThanOrEqual(44)
    await row.getByRole('button', { name: '17' }).click()
    // the margin is the next section of the page, under the list, and the page still fits
    const trace = page.getByRole('complementary')
    await expect(trace).toBeVisible()
    const traceBox = await trace.boundingBox()
    expect(traceBox!.y).toBeGreaterThan(box!.y)
    expect(await sideways(page)).toBeLessThanOrEqual(0)
  })
})
