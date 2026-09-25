import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'
import { aVisitor, API } from './stack'

/**
 * Sandbox isolation in the browser, against the real API (Document 5, Security Test Plan, End to end: "a second browser
 * context cannot see the first's sandbox"; Document 2, Sandbox isolation). Two browser contexts are two visitors, each
 * given a sandbox of its own by the gate: what the first adds and decides, the second neither sees nor can open by its
 * id. Nothing here calls a model: the policy is pasted, and the engine decides the cases.
 */

test('a second browser context sees nothing the first one added or decided', async ({
  browser,
}) => {
  const first = await aVisitor(browser)
  const second = await aVisitor(browser)
  const title = `מדיניות בדיקת בידוד ${randomUUID().slice(0, 8)}`

  // the first visitor pastes a policy of its own and decides the seeded cases
  await first.getByRole('button', { name: 'Add policy' }).click()
  await first.getByLabel('Title').fill(title)
  await first
    .getByLabel('Policy text')
    .fill('הלוואה תינתן ליחיד שגילו 21 ומעלה.\n\nהסכום המרבי הוא 100,000 ש"ח.')
  const created = first.waitForResponse(
    (response) =>
      response.url() === `${API}/api/v1/policies` && response.request().method() === 'POST',
  )
  await first.getByRole('button', { name: 'Add policy' }).last().click()
  const policy = (await (await created).json()) as { id: string }
  await expect(first.getByRole('button', { name: new RegExp(title) })).toBeVisible()
  await first
    .getByRole('navigation', { name: 'Workspace' })
    .getByRole('button', { name: 'Cases' })
    .click()
  await first.getByRole('button', { name: 'Run 200 cases' }).first().click()
  await expect(first.getByText('Rules that decided most often, of 200 decisions')).toBeVisible()

  // the second visitor lists neither, and the first one's policy is not found by its id
  await expect(second.getByRole('heading', { level: 1, name: 'Policies' })).toBeVisible()
  await expect(second.getByText(title)).toHaveCount(0)
  const opened = await second.request.get(`${API}/api/v1/policies/${policy.id}`)
  expect(opened.status()).toBe(404)
  expect(((await opened.json()) as { code: string }).code).toBe('NOT_FOUND')
  await second
    .getByRole('navigation', { name: 'Workspace' })
    .getByRole('button', { name: 'Cases' })
    .click()
  await expect(second.getByText('Nothing decided yet')).toBeVisible()
})
