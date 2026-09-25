import { expect, test } from '@playwright/test'
import { accessCode, API } from './stack'

/**
 * The access gate of the real API (Document 5, Code exchange and Security Test Plan, End to end: "a session without the
 * code sees only the gate"; the Brief's Definition of Done, line 11: "a request without the code is rejected"). The
 * stack is the one Docker Compose builds, and nothing here calls a model.
 */

test.describe('the access gate of the real API', () => {
  test('a request without the code is refused, and a session without it sees only the gate', async ({
    page,
    request,
  }) => {
    const response = await request.get(`${API}/api/v1/policies`)

    expect(response.status()).toBe(401)
    expect(((await response.json()) as { code: string }).code).toBe('SESSION_INVALID')
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1, name: 'Enter the workspace' })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Workspace' })).toHaveCount(0)
  })

  test('a wrong code is refused on the gate, and the code opens the workspace with its provider', async ({
    page,
  }) => {
    await page.goto('/')
    await page.getByLabel('Access code').fill('wrongone')
    await page.getByRole('button', { name: 'Enter' }).click()
    await expect(page.getByRole('alert')).toHaveText('That code is not valid.')

    await page.getByLabel('Access code').fill(accessCode())
    await page.getByRole('button', { name: 'Enter' }).click()

    const workspace = page.getByRole('navigation', { name: 'Workspace' })
    await expect(workspace).toBeVisible()
    // the header shows what the stack's own GET /system/provider answers, read with the session the gate opened
    const provider = (await (await page.request.get(`${API}/api/v1/system/provider`)).json()) as {
      chatModels: { strong: string }
      embeddingModel: string
    }
    const badge = workspace.getByRole('region', { name: 'Model provider' })
    await expect(badge).toContainText(provider.chatModels.strong)
    await expect(badge).toContainText(provider.embeddingModel)
  })
})
