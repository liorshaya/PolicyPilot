import { expect, type Locator, type Page } from '@playwright/test'

/**
 * The guided demo panel as a presenter uses it (Brief FR-23; Document 2, Frontend Architecture, key decision 7): the
 * code on the gate, the panel opened, and a step started with its Run button.
 */

export async function openThePanel(page: Page): Promise<void> {
  await page.goto('/')
  await page.getByLabel('Access code').fill('qwertyui')
  await page.getByRole('button', { name: 'Enter' }).click()
  await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()
  await page.getByRole('button', { name: /guided demo/i }).click()
}

/** A step's Run button: a step of the panel, not the screen of the sidebar that shares its name. */
export function step(page: Page, title: string): Locator {
  return page
    .getByRole('region', { name: 'Guided demo' })
    .getByRole('listitem')
    .filter({ hasText: title })
    .getByRole('button', { name: 'Run' })
}
