import { API_BASE_URL } from './config'

/**
 * The code exchange (Document 5, Code exchange): POST /api/v1/auth/code with the custom header and the browser's own
 * Origin; the API answers with the HttpOnly session cookie, which scripts never see. The statuses are Document 2's
 * error codes: 204 entered, 401 ACCESS_CODE_INVALID, 429 RATE_LIMITED with Retry-After (the lockout included).
 */
export type ExchangeResult =
  | { kind: 'entered' }
  | { kind: 'wrong-code' }
  | { kind: 'locked'; retryAfterSeconds: number }
  | { kind: 'failed' }

export const AUTH_CODE_URL = `${API_BASE_URL}/api/v1/auth/code`

/** Sent on every state-changing request; a cross-site form cannot add it (Document 5, CSRF). */
export const CLIENT_HEADER = 'X-PolicyPilot-Client'

/** The wait when a 429 comes without a usable Retry-After: one rate-limit window. */
const DEFAULT_RETRY_SECONDS = 60

export async function exchangeAccessCode(code: string): Promise<ExchangeResult> {
  let response: Response
  try {
    response = await fetch(AUTH_CODE_URL, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', [CLIENT_HEADER]: 'web' },
      body: JSON.stringify({ code }),
    })
  } catch {
    return { kind: 'failed' }
  }
  if (response.status === 204) {
    return { kind: 'entered' }
  }
  if (response.status === 401) {
    return { kind: 'wrong-code' }
  }
  if (response.status === 429) {
    const seconds = Number(response.headers.get('Retry-After'))
    return {
      kind: 'locked',
      retryAfterSeconds: Number.isFinite(seconds) && seconds > 0 ? seconds : DEFAULT_RETRY_SECONDS,
    }
  }
  return { kind: 'failed' }
}
