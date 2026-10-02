import { expect, test } from '@playwright/test'

// The access gate in a real browser (Document 5, Code exchange; Document 6, End to end: the access gate). The API
// is answered by the test, as Document 2's error codes say it answers, because stage 7 runs without a backend.
const AUTH_CODE = '**/api/v1/auth/code'
const AUTH_SESSION = '**/api/v1/auth/session'

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

  // Document 2, GET and DELETE /auth/session (2026-10-01): the API's cookie as a flag the stubs keep, set by the code
  // exchange and cleared by Leave. Expected: a reload while it holds opens the workspace without the code, and after
  // Leave a reload shows the gate
  test('a reload keeps a session that holds, and Leave ends it', async ({ page }) => {
    let holds = false
    await page.route(AUTH_CODE, async (route) => {
      holds = true
      await route.fulfill({ status: 204 })
    })
    await page.route(AUTH_SESSION, async (route) => {
      if (route.request().method() === 'DELETE') {
        holds = false
        await route.fulfill({ status: 204 })
        return
      }
      await route.fulfill(
        holds
          ? { status: 204 }
          : {
              status: 401,
              json: { code: 'SESSION_INVALID', message: 'm', details: [], traceId: 't' },
            },
      )
    })
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()

    await page.reload()

    await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()
    await expect(page.getByLabel('Access code')).toHaveCount(0)

    await page.getByRole('button', { name: 'Leave' }).click()
    await expect(page.getByLabel('Access code')).toBeVisible()
    expect(holds).toBe(false)
    await page.reload()

    await expect(page.getByLabel('Access code')).toBeVisible()
  })

  // the spec (v3.9), section 11, Gate: a session that ends while the workspace is open, any call answered 401
  // SESSION_INVALID, brings the gate back with the system's note, and the code opens a new one
  test('a session that ends while the workspace is open brings the gate back, saying so', async ({
    page,
  }) => {
    let ended = false
    await page.route(AUTH_CODE, async (route) => {
      ended = false
      await route.fulfill({ status: 204 })
    })
    // the one call the test answers both ways: a list while the session holds, the filter's 401 once it has ended
    await page.route('**/api/v1/rulesets', (route) =>
      route.fulfill(
        ended
          ? {
              status: 401,
              json: { code: 'SESSION_INVALID', message: 'm', details: [], traceId: 't' },
            }
          : { json: { rulesets: [] } },
      ),
    )
    await page.goto('/')
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    const workspace = page.getByRole('navigation', { name: 'Workspace' })
    await expect(workspace).toBeVisible()

    ended = true
    await workspace.getByRole('button', { name: /^Rules/ }).click()

    await expect(page.getByRole('note', { name: 'Session' })).toHaveText(
      'Your session has ended. Enter the code again.',
    )
    await page.getByLabel('Access code').fill('qwertyui')
    await page.getByRole('button', { name: 'Enter' }).click()
    await expect(workspace).toBeVisible()
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
