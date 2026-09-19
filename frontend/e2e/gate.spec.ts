import { expect, test } from '@playwright/test'

// The access gate in a real browser (Document 5, Code exchange; Document 6, End to end: the access gate). The API
// is answered by the test, as Document 2's error codes say it answers, because stage 7 runs without a backend.
const AUTH_CODE = '**/api/v1/auth/code'

test.describe('access gate', () => {
  test('renders the gate with the code field and a disabled button', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('img', { name: 'PolicyPilot' })).toBeVisible()
    await expect(page.getByRole('heading', { level: 1, name: 'Enter the workspace' })).toBeVisible()
    await expect(page.getByLabel('Access code')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Enter' })).toBeDisabled()
  })

  test('enables the button once a code is typed', async ({ page }) => {
    await page.goto('/')

    await page.getByLabel('Access code').fill('demo1234')

    await expect(page.getByRole('button', { name: 'Enter' })).toBeEnabled()
  })

  test('a valid code opens the demo and the exchange carries the client header', async ({
    page,
  }) => {
    let clientHeader: string | undefined
    await page.route(AUTH_CODE, async (route) => {
      clientHeader = route.request().headers()['x-policypilot-client']
      await route.fulfill({ status: 204 })
    })
    await page.goto('/')

    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()

    await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()
    expect(clientHeader).toBe('web')
  })

  test('a wrong code is refused on the gate', async ({ page }) => {
    await page.route(AUTH_CODE, (route) =>
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: '{"code":"ACCESS_CODE_INVALID"}',
      }),
    )
    await page.goto('/')

    await page.getByLabel('Access code').fill('wrongone')
    await page.getByRole('button', { name: 'Enter' }).click()

    await expect(page.getByRole('alert')).toHaveText('That code is not valid.')
  })
})
