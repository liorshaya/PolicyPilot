import { expect, test } from '@playwright/test'
import { casesExpected, changeRequest, serveTheChange } from './change'
import { question, serveTheChat } from './chat'
import { openThePanel, step } from './panel'
import { paragraphs, serveTheSeededRuleSet } from './seeded'

// @requirement FR-17
// @requirement FR-18
// @requirement FR-19
// @requirement FR-23

/**
 * The guided demo panel in a real browser (Brief FR-23; Document 2, Frontend Architecture; Work Plan days 11 and 14).
 * The panel is how the demo is driven from an interviewer's machine, so what this proves is that its steps land on the
 * right screen with the right input already filled in, that step 3 then answers all three scripted questions, and that
 * step 4 goes from the request to version 2 and its audit entry. The questions are the labeled set's
 * (fixtures/eval/questions.json, Q-01 to Q-03) and the change request its CR-1, never typed out here.
 */

test.describe('the guided demo panel', () => {
  test.beforeEach(async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await serveTheChat(page)
    await serveTheChange(page)
  })

  test('step 3 answers all three scripted questions, each with its citation', async ({ page }) => {
    await openThePanel(page)

    await step(page, 'Ask').click()

    await expect(page.getByRole('heading', { level: 1, name: 'Assistant' })).toBeVisible()
    // the panel fills the first question in; a presenter presses Ask and types nothing
    await expect(page.getByLabel('Question', { exact: true })).toHaveValue(
      question('Q-01').question,
    )
    // the question it fills in is Hebrew, so the composer has to turn around with it (Document 5, Hebrew pitfalls)
    await expect(page.getByLabel('Question', { exact: true })).toHaveAttribute('dir', 'auto')
    await page.getByRole('button', { name: 'Ask' }).click()
    await expect(
      page.getByRole('group', { name: 'Sources' }).getByRole('button', { name: 'Case 17' }),
    ).toBeVisible()

    await page.getByLabel('Question', { exact: true }).fill(question('Q-02').question)
    await page.getByRole('button', { name: 'Ask' }).click()
    await expect(page.getByText('עם ערב הבקשה הייתה מאושרת')).toBeVisible()

    await page.getByLabel('Question', { exact: true }).fill(question('Q-03').question)
    await page.getByRole('button', { name: 'Ask' }).click()
    await expect(page.getByText('84 חודשים')).toBeVisible()
    await expect(
      page
        .getByRole('group', { name: 'Sources' })
        .last()
        .getByRole('button', { name: 'Paragraph 2' }),
    ).toBeVisible()
  })

  test('step 1 opens the policy form already holding the sample policy', async ({ page }) => {
    await openThePanel(page)

    await step(page, 'Author').click()

    await expect(page.getByRole('heading', { level: 1, name: 'Policies' })).toBeVisible()
    // the form opens holding the seeded policy's own paragraphs, read back through the API, not a copy
    await expect(page.getByLabel('Policy text')).toHaveValue(
      paragraphs.map((paragraph) => paragraph.text).join('\n\n'),
    )
  })

  test('step 2 lands on the cases screen and decides the seeded set', async ({ page }) => {
    await openThePanel(page)

    await step(page, 'Decide').click()

    await expect(page.getByRole('heading', { level: 1, name: 'Cases' })).toBeVisible()
  })

  test('step 4 proposes the scripted change, lists the 12 flips, and its approval publishes version 2 with its audit entry', async ({
    page,
  }) => {
    const note = 'אושר בוועדת האשראי'
    await openThePanel(page)

    await step(page, 'Change').click()

    await expect(page.getByRole('heading', { level: 1, name: 'Change' })).toBeVisible()
    // the panel types the labeled request; the presenter proposes it
    await expect(page.getByLabel('What should change')).toHaveValue(changeRequest.text.he)
    await page.getByRole('button', { name: 'Propose the change' }).click()

    // the two rules the change touches, each patch with its operation and its rationale (Brief, demo step 4)
    const patches = page.getByRole('list', { name: 'Patches' }).getByRole('listitem')
    await expect(patches).toHaveCount(2)
    await expect(patches.nth(0).locator('.patch__op')).toHaveText('Replace')
    await expect(patches.nth(0).getByText('R-170', { exact: true })).toBeVisible()
    await expect(patches.nth(1).getByText('R-410', { exact: true })).toBeVisible()
    // one row per changed cell: R-170's threshold, 8,000 struck and 9,000 inserted (Document 3, Structural diff)
    const threshold = page
      .getByRole('region', { name: 'Changes from Published v1 to Proposed' })
      .locator('.udiff__row')
      .filter({ hasText: 'R-170' })
      .filter({ hasText: 'monthly_income' })
    await expect(threshold.locator('.del')).toHaveText('8,000')
    await expect(threshold.locator('.add')).toHaveText('9,000')
    // the 200 cases decided again: 12 decisions flip, listed by case (cases-expected.json, regression)
    const report = page.getByRole('region', { name: 'Regression report' })
    await expect(report.locator('.figure').first()).toContainText('126.0% of 200')
    await report.getByRole('button', { name: 'Show all 12' }).click()
    await expect(
      report
        .getByRole('table', { name: 'The decisions that flip' })
        .locator('tbody tr td:first-child'),
    ).toHaveText(casesExpected.regression.flips.map((flip) => String(flip.id)))

    await page.getByLabel('Note for the audit log').fill(note)
    await page.getByRole('button', { name: 'Approve and publish v2' }).click()

    await expect(
      page.getByText(
        "Approved. Version 2 is published in this sandbox's own copy of the rule set; the cases decide on it from now on.",
      ),
    ).toBeVisible()
    await expect(page.locator('.seal .seal__line')).toHaveText('CR-0001 · 2026-09-27 09:12')
    // the workspace follows the approval to version 2 of the sandbox's copy
    await expect(page.getByText('Version 2', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: 'Open the audit log' }).click()

    await expect(page.getByRole('heading', { level: 1, name: 'Audit log' })).toBeVisible()
    const entry = page.getByRole('region', { name: 'Audit log' }).getByRole('listitem').first()
    await expect(entry.locator('.event__verb')).toHaveText('Change approved')
    await expect(entry).toContainText('CR-0001')
    await expect(entry).toContainText(note)

    await entry.getByText('Show the request, the diff and the regression').click()

    await expect(entry.locator('.event__more')).toContainText(changeRequest.text.he)
    await expect(entry.getByRole('region', { name: 'Changes from v1 to v2' })).toBeVisible()
  })
})
