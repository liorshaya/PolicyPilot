import { expect, test, type Page } from '@playwright/test'
import { serveTheGeneration } from './generation'
import { serveTheWholePolicy, serveTheWholeRun } from './lending'
import { serveTheSeededRuleSet } from './seeded'

// @requirement NFR-2

/**
 * Go to anything (the spec, section 08; Document 9, phase 6): ⌘K opens the palette on any screen, a typed identifier
 * lists what it names, grouped by kind, each row with its chip, its state and what opening does, and opening it goes
 * there. What a case number reaches is this session's run of the cases (the owner's answer to phase 6's second
 * question). Every API call is answered from the committed fixtures.
 */

async function enter(page: Page): Promise<void> {
  await serveTheSeededRuleSet(page)
  await serveTheWholePolicy(page)
  await serveTheWholeRun(page)
  await serveTheGeneration(page)
  await page.goto('/')
  await page.getByLabel('Access code').fill('qwertyui')
  await page.getByRole('button', { name: 'Enter' }).click()
  await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()
}

/** The palette's own rows; a screen's select boxes have options of their own. */
const rows = (page: Page) => page.getByRole('dialog', { name: 'Go to' }).getByRole('option')

/** Opens the palette from the keyboard and types into it. */
async function goTo(page: Page, query: string) {
  await page.keyboard.press('ControlOrMeta+K')
  const input = page.getByRole('combobox', { name: 'Go to' })
  await expect(input).toBeFocused()
  await input.fill(query)
  return input
}

async function runTheCases(page: Page): Promise<void> {
  await page
    .getByRole('navigation', { name: 'Workspace' })
    .getByRole('button', { name: /^Cases/ })
    .click()
  await page.getByRole('button', { name: 'Run 200 cases' }).first().click()
  await expect(page.getByRole('button', { name: '17', exact: true })).toBeVisible()
}

/** The workspace on the draft step 1 writes, with the reviewer's findings. */
async function writeTheDraft(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Generate rules' }).click()
  await page.getByRole('button', { name: 'Review the draft' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Rules' })).toBeVisible()
}

test.describe('the palette', () => {
  test.beforeEach(async ({ page }) => {
    await enter(page)
  })

  test('opens with ⌘K on any screen and closes with Esc', async ({ page }) => {
    await goTo(page, '')
    const palette = page.getByRole('dialog', { name: 'Go to' })
    await expect(palette).toContainText('A case number, R-330, ¶ 7, CR-0001, F-1 or v1')

    await page.keyboard.press('Escape')
    await expect(palette).toHaveCount(0)
  })

  test("lists Case 17 for 17, with its decision tag and 'open the trace', and opens its trace", async ({
    page,
  }) => {
    await runTheCases(page)
    await page
      .getByRole('navigation', { name: 'Workspace' })
      .getByRole('button', { name: /^Rules/ })
      .click()

    await goTo(page, '17')
    const first = rows(page).first()
    await expect(first).toHaveAttribute('aria-selected', 'true')
    // cases-expected.json: case 17 goes to manual review
    await expect(first).toHaveText(/^Case 17\s*Manual review\s*open the trace$/)
    await page.keyboard.press('Enter')

    await expect(page.getByRole('heading', { level: 1, name: 'Cases' })).toBeVisible()
    await expect(page.getByRole('complementary', { name: 'Case 17' })).toBeVisible()
  })

  test('says a number reaches a case once the cases have run', async ({ page }) => {
    await goTo(page, '17')

    await expect(page.getByRole('dialog', { name: 'Go to' })).toContainText(
      'Run the cases to reach a case by its number',
    )
    await expect(rows(page)).toHaveText([/^R-170/])
  })

  test("lists R-170 with its Hebrew label and 'open in the table', and opens it there", async ({
    page,
  }) => {
    await goTo(page, 'R-170')
    const row = rows(page)
    await expect(row).toHaveCount(1)
    // ruleset.v1.json's label for R-170, in the policy's language
    const label = row.getByText('דחייה: הכנסה חודשית נטו נמוכה מ-8,000')
    await expect(label).toHaveAttribute('lang', 'he')
    await expect(row).toContainText('open in the table')
    await page.keyboard.press('Enter')

    await expect(page.getByRole('heading', { level: 1, name: 'Rules' })).toBeVisible()
    await expect(page.getByRole('complementary')).toContainText('R-170')
  })

  test('resolves ¶ 4 to the paragraph, opened in the policy', async ({ page }) => {
    await goTo(page, '¶ 4')
    await expect(rows(page)).toHaveText([/^¶\s4\s*open in the policy$/])
    await page.keyboard.press('Enter')

    await expect(page.getByRole('heading', { level: 1, name: 'Policies' })).toBeVisible()
    const paragraph = page.locator('#paragraph-4')
    await expect(paragraph).toHaveClass(/para--cited/)
    await expect(paragraph).toBeInViewport()
  })

  test("resolves F-1 to the review's finding, opened in the review", async ({ page }) => {
    await writeTheDraft(page)

    await goTo(page, 'F-1')
    // generation.ts's review: F-1 is the ambiguity of "stable income"
    await expect(rows(page)).toHaveText([/^F-1\s*Ambiguity\s*open in the review$/])
    await page.keyboard.press('Enter')

    const finding = page.getByRole('complementary').locator('.finding[aria-current="true"]')
    await expect(finding).toContainText('F-1')
    await expect(finding).toBeInViewport()
  })

  test('moves the selection with the arrow keys and opens the selected row with Enter', async ({
    page,
  }) => {
    await runTheCases(page)

    const input = await goTo(page, '17')
    const listed = rows(page)
    await page.keyboard.press('ArrowDown')
    await expect(listed.nth(1)).toHaveAttribute('aria-selected', 'true')
    await expect(input).toHaveAttribute(
      'aria-activedescendant',
      (await listed.nth(1).getAttribute('id'))!,
    )
    await page.keyboard.press('ArrowUp')
    await expect(listed.nth(0)).toHaveAttribute('aria-selected', 'true')
    await page.keyboard.press('Enter')

    await expect(page.getByRole('complementary', { name: 'Case 17' })).toBeVisible()
    await expect(page.getByRole('dialog', { name: 'Go to' })).toHaveCount(0)
  })

  test("opens from the header's Go to", async ({ page }) => {
    await runTheCases(page)

    await page.getByRole('button', { name: /^Go to/ }).click()

    await expect(page.getByRole('combobox', { name: 'Go to' })).toBeFocused()
  })
})
