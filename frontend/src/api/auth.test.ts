import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/msw/server'
import { AUTH_CODE_URL, exchangeAccessCode } from './auth'

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
