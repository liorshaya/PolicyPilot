import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/msw/server'
import { AUTH_CODE_URL, AUTH_SESSION_URL, exchangeAccessCode, leave, sessionHolds } from './auth'

// Document 5, Code exchange; the statuses and codes are Document 2's error codes table.
describe('exchangeAccessCode', () => {
  it('sends the code as JSON with the client header', async () => {
    let seen: { header: string | null; body: unknown } | undefined
    server.use(
      http.post(AUTH_CODE_URL, async ({ request }) => {
        seen = { header: request.headers.get('X-PolicyPilot-Client'), body: await request.json() }
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const result = await exchangeAccessCode('qwertyui')

    expect(result).toEqual({ kind: 'entered' })
    expect(seen).toEqual({ header: 'web', body: { code: 'qwertyui' } })
  })

  it('reads 401 as a wrong code', async () => {
    server.use(
      http.post(AUTH_CODE_URL, () =>
        HttpResponse.json({ code: 'ACCESS_CODE_INVALID' }, { status: 401 }),
      ),
    )

    expect(await exchangeAccessCode('wrongone')).toEqual({ kind: 'wrong-code' })
  })

  it('reads 429 as a lockout with the Retry-After seconds', async () => {
    server.use(
      http.post(AUTH_CODE_URL, () =>
        HttpResponse.json(
          { code: 'RATE_LIMITED' },
          { status: 429, headers: { 'Retry-After': '840' } },
        ),
      ),
    )

    expect(await exchangeAccessCode('qwertyui')).toEqual({ kind: 'locked', retryAfterSeconds: 840 })
  })

  it('waits one minute when a 429 has no usable Retry-After', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 429 })))

    expect(await exchangeAccessCode('qwertyui')).toEqual({ kind: 'locked', retryAfterSeconds: 60 })
  })

  it('reads any other status as a failure', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 403 })))

    expect(await exchangeAccessCode('qwertyui')).toEqual({ kind: 'failed' })
  })

  it('reads a network error as a failure', async () => {
    server.use(http.post(AUTH_CODE_URL, () => HttpResponse.error()))

    expect(await exchangeAccessCode('qwertyui')).toEqual({ kind: 'failed' })
  })
})

// Document 2, GET /auth/session (2026-10-01): the app asks it once on load; 204 is a session that holds, and anything
// else, the filter's 401 or no answer at all, sends the visitor to the gate
describe('sessionHolds', () => {
  it('reads 204 as a session that holds, asked with the cookie', async () => {
    let credentialsSent = false
    server.use(
      http.get(AUTH_SESSION_URL, ({ request }) => {
        credentialsSent = request.credentials === 'include'
        return new HttpResponse(null, { status: 204 })
      }),
    )

    expect(await sessionHolds()).toBe(true)
    expect(credentialsSent).toBe(true)
  })

  it('reads 401 as no session', async () => {
    server.use(
      http.get(AUTH_SESSION_URL, () =>
        HttpResponse.json(
          { code: 'SESSION_INVALID', message: 'm', details: [], traceId: 't' },
          { status: 401 },
        ),
      ),
    )

    expect(await sessionHolds()).toBe(false)
  })

  it('reads a network error as no session', async () => {
    server.use(http.get(AUTH_SESSION_URL, () => HttpResponse.error()))

    expect(await sessionHolds()).toBe(false)
  })
})

// Document 2, DELETE /auth/session: Leave, a state-changing request, so it carries the client header
describe('leave', () => {
  it('sends DELETE with the client header and the cookie', async () => {
    let seen: { header: string | null; credentials: string } | undefined
    server.use(
      http.delete(AUTH_SESSION_URL, ({ request }) => {
        seen = {
          header: request.headers.get('X-PolicyPilot-Client'),
          credentials: request.credentials,
        }
        return new HttpResponse(null, { status: 204 })
      }),
    )

    await leave()

    expect(seen).toEqual({ header: 'web', credentials: 'include' })
  })

  it('settles when the API cannot be reached, so the visitor still leaves', async () => {
    server.use(http.delete(AUTH_SESSION_URL, () => HttpResponse.error()))

    await expect(leave()).resolves.toBeUndefined()
  })
})
