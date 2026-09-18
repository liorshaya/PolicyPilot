import { expect, test } from '@playwright/test'

// Work Plan day 1: a Playwright test that the gate page renders (Document 5, access gate; Document 6, End to end).
test.describe('access gate', () => {
  test('renders the gate with the code field and a disabled button', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { level: 1, name: 'PolicyPilot' })).toBeVisible()
    await expect(page.getByLabel('Access code')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Enter' })).toBeDisabled()
  })

  test('enables the button once a code is typed', async ({ page }) => {
    await page.goto('/')

    await page.getByLabel('Access code').fill('demo1234')

    await expect(page.getByRole('button', { name: 'Enter' })).toBeEnabled()
  })
})
