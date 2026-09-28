import { describe, expect, it } from 'vitest'
import { decisionFailureText, proposeFailureText } from './failures'

/**
 * The refusals of the change routes in the words the analyst acts on (Document 2, API Surface: the change request is
 * refused with 400, 404, 409 or 429 before any stream opens, or ends with an `error` event; approve and reject with 400,
 * 404 or 409; a model call the day's budget stops answers BUDGET_EXHAUSTED). Every code the routes document has a
 * sentence of its own. The refusal block that shows them closes with "Nothing was stored." itself (the spec, section
 * 08), so no sentence says it again.
 */
const PROPOSE_CODES: [string, string][] = [
  [
    'VERSION_STATUS_CONFLICT',
    'Only a published version that has finished indexing takes a change. Try again in a moment.',
  ],
  ['RATE_LIMITED', 'Too many requests in a short time. Wait a minute, then try again.'],
  ['REQUEST_INVALID', 'The request is empty, too long, or holds a character it may not carry.'],
  ['NOT_FOUND', 'This sandbox cannot see that version.'],
  ['PROVIDER_UNAVAILABLE', 'The model did not answer in time. Try again.'],
  [
    'BUDGET_EXHAUSTED',
    "Today's model budget is spent; the demo's change request is still proposed from the cache, and any other waits until the budget resumes.",
  ],
  ['INTERNAL_ERROR', 'Try again.'],
]

const DECISION_CODES: [string, string][] = [
  [
    'VERSION_STATUS_CONFLICT',
    'The version it was proposed on is no longer the latest, or this sandbox already has its own copy of the seeded rule set. Propose the change again on the latest version.',
  ],
  ['REQUEST_INVALID', 'The note is too long or holds a character it may not carry.'],
  ['NOT_FOUND', 'This change request is not one this sandbox can see.'],
  ['INTERNAL_ERROR', 'Try again.'],
]

describe('proposeFailureText', () => {
  it.each(PROPOSE_CODES)('says what %s means for the request', (code, expected) => {
    expect(proposeFailureText(code)).toBe(expected)
  })
})

describe('decisionFailureText', () => {
  it.each(DECISION_CODES)('says what %s means for the decision', (code, expected) => {
    expect(decisionFailureText(code)).toBe(expected)
  })
})

describe('the refusal sentences', () => {
  it('leave "Nothing was stored." to the refusal block that closes with it', () => {
    for (const [code] of PROPOSE_CODES) {
      expect(proposeFailureText(code)).not.toContain('Nothing was stored.')
    }
    for (const [code] of DECISION_CODES) {
      expect(decisionFailureText(code)).not.toContain('Nothing was stored.')
    }
  })
})
