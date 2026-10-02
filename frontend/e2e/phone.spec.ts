import { expect, test, type Page } from '@playwright/test'
import { changeRequest, serveTheChange } from './change'
import { ask, question, serveTheChat } from './chat'
import { inspect } from './checklist'
import { serveTheWholeRun } from './lending'
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

// The edge cases of 2026-10-01: the gate's 400px sheet sat in a grid column as wide as itself, so on every phone
// narrower than 424px the first screen scrolled sideways (35px at 390). Expected: the spec's gate (v3.8, its column
// held to the window) fits the narrowest common phone and this spec's one
for (const width of [320, 390]) {
  test(`the gate fits a ${String(width)}px phone with nothing scrolling sideways`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 800 })
    await page.goto('/')
    await expect(page.getByLabel('Access code')).toBeVisible()

    expect(await sideways(page)).toBeLessThanOrEqual(0)
  })
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
    // the margin is the next section of the page, under the list, and the page still fits; both read after the click,
    // which brings the trace into view (v3.9)
    const trace = page.getByRole('complementary')
    await expect(trace).toBeVisible()
    const traceBox = await trace.boundingBox()
    expect(traceBox!.y).toBeGreaterThan((await row.boundingBox())!.y)
    expect(await sideways(page)).toBeLessThanOrEqual(0)
  })
})

test.describe('on a narrower phone', () => {
  test.use({ viewport: { width: 360, height: 780 } })

  // the step line of a what-if is the widest thing an answer holds; on a narrow line its chip breaks between its words,
  // each word whole, instead of widening the turn past the window (CI stage 7 of #172 found it 11px past at 390)
  test("keeps the assistant's turns inside the window, a tool call's chip breaking between its words", async ({
    page,
  }) => {
    await enter(page)
    await open(page, 'Assistant')
    await ask(page, question('Q-02').question)
    await expect(page.getByRole('list', { name: 'Tool calls' })).toContainText(
      'what-if · case 17 · has_guarantor=true',
    )

    expect((await inspect(page)).overflow).toEqual([])
  })
})

// The pass over every width of 2026-10-02 (the spec, section 10, v3.9): on a phone the margin is the next section of the
// page, under the list, so a case chosen among the 200 opened its trace some 7,500px below, out of sight, and nothing
// seemed to happen; a chosen row now brings the margin into view, and the trace's Close takes the reader back
test.describe('the margin on a phone', () => {
  test("brings a chosen case's trace into view, and its Close takes the reader back to the row", async ({
    page,
  }) => {
    await enter(page)
    // the 200 cases of cases-expected.json, over the one case the other tests run
    await serveTheWholeRun(page)
    await open(page, 'Cases')
    await page.getByRole('button', { name: 'Run 200 cases' }).first().click()
    const row = page.getByRole('button', { name: '17', exact: true })

    await row.tap()
    const heading = page.getByRole('heading', { level: 2, name: /^Case 17/ })
    await expect(heading).toBeInViewport()
    // at the top of the window, the trace under it: a scroll made before the trace was read stopped at the page's
    // end, the trace's head at the window's foot
    expect((await heading.boundingBox())!.y).toBeLessThan(120)
    await page.getByRole('complementary').getByRole('button', { name: 'Close' }).tap()

    await expect(row).toBeInViewport()
  })

  test('brings a chosen rule into view under the rule list', async ({ page }) => {
    await enter(page)
    await open(page, 'Rules')

    // the first rule, so the margin stands a whole list of rules below it (R-010 cites paragraph 5, ruleset.v1.json)
    await page.getByRole('table').getByRole('button', { name: /R-010/ }).tap()

    await expect(page.getByText(/Paragraph 5 is the source of R-010/)).toBeInViewport()
  })

  // the spec, section 10: "chips 24px tall" on a phone, a touch target; they stood at the desktop's 20px
  test('makes a chip a 24px touch target', async ({ page }) => {
    await enter(page)

    const chip = page.getByRole('button', { name: 'R-330', exact: true })
    await expect(chip).toBeVisible()
    expect((await chip.boundingBox())!.height).toBe(24)
  })
})

test.describe('on the narrowest phone', () => {
  test.use({ viewport: { width: 320, height: 640 } })

  // the spec (v3.9), section 08: a popover keeps inside the window; the menu, 320px wide and lined up with its button's
  // end, started 14px before the window, and Help after it too
  test('keeps the menu and the legend behind Help inside the window', async ({ page }) => {
    await enter(page)

    await page.getByRole('button', { name: 'Menu' }).click()
    await expect(page.getByRole('dialog', { name: 'Menu' })).toBeVisible()
    expect((await inspect(page)).overflow, 'Menu').toEqual([])
    await page.getByRole('dialog', { name: 'Menu' }).getByRole('button', { name: 'Help' }).click()
    await expect(page.getByRole('dialog', { name: 'Help' })).toBeVisible()
    expect((await inspect(page)).overflow, 'Help').toEqual([])
  })

  // the spec, section 10 (v3.9): the decision table is a rule list on a phone; it kept every column, and at 320px its
  // frozen action column stood over the frozen label column, covering the start of each Hebrew label
  test('draws the rules as a rule list, the label and id, then the action, neither covering the other', async ({
    page,
  }) => {
    await enter(page)
    await open(page, 'Rules')

    const row = page.getByRole('row').filter({ hasText: 'R-010' })
    const label = (await row.getByRole('rowheader').boundingBox())!
    const action = (await row.getByRole('cell').last().boundingBox())!
    expect(label.x + label.width).toBeLessThanOrEqual(action.x + 1)
    await expect(page.getByRole('columnheader', { name: 'Priority' })).toBeHidden()
    expect(await sideways(page)).toBeLessThanOrEqual(0)
  })
})
