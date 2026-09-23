import { describe, expect, it } from 'vitest'
import { decisionFailureText, proposeFailureText } from './failures'

/**
 * The refusals of the change routes in the words the analyst acts on (Document 2, API Surface: the change request is
 * refused with 400, 404, 409 or 429 before any stream opens, or ends with an `error` event; approve and reject with 400,
 * 404 or 409). Every code the routes document has a sentence of its own; anything else says nothing was stored.
 */
describe('proposeFailureText', () => {
  it.each([
    [
      'VERSION_STATUS_CONFLICT',
      'Only a published version that has finished indexing takes a change.',
    ],
    ['RATE_LIMITED', 'Too many requests'],
    ['REQUEST_INVALID', 'The request is empty, too long'],
    ['NOT_FOUND', 'cannot see that version'],
    ['PROVIDER_UNAVAILABLE', 'The model did not answer in time.'],
    ['INTERNAL_ERROR', 'The change could not be proposed.'],
  ])('says what %s means for the request', (code, expected) => {
    expect(proposeFailureText(code)).toContain(expected)
  })

  it('says nothing was stored whenever the proposal did not arrive', () => {
    for (const code of ['REQUEST_INVALID', 'PROVIDER_UNAVAILABLE', 'INTERNAL_ERROR']) {
      expect(proposeFailureText(code)).toContain('Nothing was stored.')
    }
  })
})

describe('decisionFailureText', () => {
  it.each([
    ['VERSION_STATUS_CONFLICT', 'no longer the latest, or this sandbox already has its own copy'],
    ['REQUEST_INVALID', 'The note is too long'],
    ['NOT_FOUND', 'not one this sandbox can see'],
    ['INTERNAL_ERROR', 'Nothing was published.'],
  ])('says what %s means for the decision', (code, expected) => {
    expect(decisionFailureText(code)).toContain(expected)
  })
})
