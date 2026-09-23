import { API_BASE_URL } from './config'
import { CLIENT_HEADER } from './auth'
import type {
  Audience,
  AuditEntriesResponse,
  BatchResult,
  ChangeDecision,
  Diff,
  ChatSessionResponse,
  Decision,
  ErrorEnvelope,
  Explanation,
  GapResolution,
  PoliciesResponse,
  PolicyResponse,
  RulesetsResponse,
  RuleSetDocument,
  VersionResponse,
  Aggregates,
} from './types'

/**
 * The typed API client (Document 2, Frontend Architecture: the generated client, the error envelope type and the
 * custom header on every request). The session is the HttpOnly cookie, so every call sends credentials and the app
 * never holds a token of its own.
 */

/** A refusal from the API, carrying the envelope of Document 2 so a screen can show the code and the pointers. */
export class ApiError extends Error {
  readonly status: number
  readonly envelope: ErrorEnvelope | null

  constructor(status: number, envelope: ErrorEnvelope | null) {
    super(envelope?.code ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.envelope = envelope
  }

  /** The error code of the envelope, or the status when the body was not an envelope. */
  get code(): string {
    return this.envelope?.code ?? `HTTP_${this.status}`
  }

  /** The pointer details, which the rule editor shows on the cells they name. */
  get details(): NonNullable<ErrorEnvelope['details']> {
    return this.envelope?.details ?? []
  }
}

type Query = Record<string, string | number | undefined>

function url(path: string, query?: Query): string {
  const address = new URL(`${API_BASE_URL}${path}`)
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined) {
      address.searchParams.set(key, String(value))
    }
  }
  return address.toString()
}

async function request<T>(
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  options: { body?: unknown; formData?: FormData; accept?: string; query?: Query } = {},
): Promise<T> {
  const headers: Record<string, string> = { [CLIENT_HEADER]: 'web' }
  if (options.accept) {
    headers.Accept = options.accept
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(url(path, options.query), {
    method,
    credentials: 'include',
    headers,
    body:
      options.formData ?? (options.body === undefined ? undefined : JSON.stringify(options.body)),
  })
  if (!response.ok) {
    throw new ApiError(response.status, await envelopeOf(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function envelopeOf(response: Response): Promise<ErrorEnvelope | null> {
  try {
    return (await response.json()) as ErrorEnvelope
  } catch {
    return null
  }
}

/** Where a decision's export is downloaded from; the browser follows the link and the API sets the file name. */
export function decisionExportUrl(decisionId: string): string {
  return url(`/api/v1/decisions/${decisionId}/export`)
}

export const api = {
  createPolicy: (body: { title: string; language: 'he' | 'en'; text: string }) =>
    request<PolicyResponse>('POST', '/api/v1/policies', { body }),

  uploadPolicy: (file: File, title: string, language: 'he' | 'en') => {
    const form = new FormData()
    form.append('file', file)
    form.append('title', title)
    form.append('language', language)
    return request<PolicyResponse>('POST', '/api/v1/policies', { formData: form })
  },

  policies: () => request<PoliciesResponse>('GET', '/api/v1/policies'),

  policy: (policyId: string) => request<PolicyResponse>('GET', `/api/v1/policies/${policyId}`),

  rulesets: () => request<RulesetsResponse>('GET', '/api/v1/rulesets'),

  version: (rulesetId: string, versionNo: number) =>
    request<VersionResponse>('GET', `/api/v1/rulesets/${rulesetId}/versions/${versionNo}`),

  replaceRules: (rulesetId: string, versionNo: number, document: RuleSetDocument) =>
    request<VersionResponse>('PUT', `/api/v1/rulesets/${rulesetId}/versions/${versionNo}/rules`, {
      body: document,
    }),

  publish: (rulesetId: string, versionNo: number) =>
    request<VersionResponse>('POST', `/api/v1/rulesets/${rulesetId}/versions/${versionNo}/publish`),

  review: (rulesetId: string, versionNo: number) =>
    request<VersionResponse>('POST', `/api/v1/rulesets/${rulesetId}/versions/${versionNo}/review`),

  acknowledge: (
    rulesetId: string,
    versionNo: number,
    findingId: string,
    body: { resolution?: GapResolution; note?: string },
  ) =>
    request<VersionResponse>(
      'POST',
      `/api/v1/rulesets/${rulesetId}/versions/${versionNo}/findings/${findingId}/acknowledge`,
      { body },
    ),

  explain: (decisionId: string, audience: Audience) =>
    request<Explanation>('POST', `/api/v1/decisions/${decisionId}/explain`, {
      body: { audience },
    }),

  decideCase: (rulesetId: string, versionNo: number, input: Record<string, unknown>) =>
    request<Decision>('POST', `/api/v1/rulesets/${rulesetId}/versions/${versionNo}/decide`, {
      body: { case: input },
    }),

  decideFixtureSet: (rulesetId: string, versionNo: number, fixtureSet: string) =>
    request<BatchResult>('POST', `/api/v1/rulesets/${rulesetId}/versions/${versionNo}/decide`, {
      body: { fixtureSet },
    }),

  decision: (decisionId: string) => request<Decision>('GET', `/api/v1/decisions/${decisionId}`),

  openChat: (rulesetId: string, versionNo: number) =>
    request<ChatSessionResponse>('POST', '/api/v1/chat/sessions', {
      body: { rulesetId, versionNo },
    }),

  stats: (rulesetId: string, versionNo: number) =>
    request<Aggregates>('GET', `/api/v1/rulesets/${rulesetId}/versions/${versionNo}/stats`),

  /** A version's audit entries, newest first (Document 2, GET /audit). */
  audit: (versionId: string) =>
    request<AuditEntriesResponse>('GET', '/api/v1/audit', { query: { versionId } }),

  /** The structural diff of two versions of one rule set (Document 2, GET .../diff/{b}). */
  diff: (rulesetId: string, from: number, to: number) =>
    request<Diff>('GET', `/api/v1/rulesets/${rulesetId}/versions/${from}/diff/${to}`),

  /** A person's decision on a proposed change; a blank note is no note (Document 2: the note is optional). */
  decideChange: (changeId: string, verdict: 'approve' | 'reject', note: string) =>
    request<ChangeDecision>('POST', `/api/v1/changes/${changeId}/${verdict}`, {
      body: note.trim() === '' ? {} : { note },
    }),

  simulate: (
    rulesetId: string,
    versionNo: number,
    body: {
      decisionId?: string
      case?: Record<string, unknown>
      overrides: Record<string, unknown>
    },
  ) =>
    request<Decision>('POST', `/api/v1/rulesets/${rulesetId}/versions/${versionNo}/simulate`, {
      body,
    }),
}
