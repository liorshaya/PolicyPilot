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

/** Whether the session holds (GET) and Leave (DELETE), Document 2 (2026-10-01). */
export const AUTH_SESSION_URL = `${API_BASE_URL}/api/v1/auth/session`

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

/**
 * Whether the session cookie still holds (Document 2, GET /auth/session), asked once on load so a reload opens the
 * workspace without the code; the filter's 401, any other answer and no answer at all mean the gate.
 */
export async function sessionHolds(): Promise<boolean> {
  try {
    const response = await fetch(AUTH_SESSION_URL, { credentials: 'include' })
    return response.status === 204
  } catch {
    return false
  }
}

/**
 * Leave (Document 2, DELETE /auth/session): the API expires the cookie in this browser. A failure is not the visitor's
 * to handle, so it settles either way and the gate follows.
 */
export async function leave(): Promise<void> {
  try {
    await fetch(AUTH_SESSION_URL, {
      method: 'DELETE',
      credentials: 'include',
      headers: { [CLIENT_HEADER]: 'web' },
    })
  } catch {
    // the cookie then expires on its own, 24 hours after its last renewal (Document 5)
  }
}
