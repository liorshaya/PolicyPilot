import { expect, type Browser, type Page } from '@playwright/test'

/**
 * The stack the tests run against: the API's address (the web app's own comes from the config's baseURL) and its
 * access code, which the tests are given rather than know, since every stack has its own.
 */

export const API = process.env.STACK_API_URL ?? 'http://localhost:8080'

export function accessCode(): string {
  const code = process.env.E2E_ACCESS_CODE
  if (code === undefined || code === '') {
    throw new Error(
      'E2E_ACCESS_CODE is not set: the tests enter the access code of the stack they run against',
    )
  }
  return code
}

/** A visitor of their own: a new browser context, through the gate, so the API gives it a sandbox of its own. */
export async function aVisitor(browser: Browser): Promise<Page> {
  const page = await (await browser.newContext()).newPage()
  await page.goto('/')
  await page.getByLabel('Access code').fill(accessCode())
  await page.getByRole('button', { name: 'Enter' }).click()
  await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()
  return page
}
