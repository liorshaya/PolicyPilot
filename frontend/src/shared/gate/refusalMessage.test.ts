import { describe, expect, it } from 'vitest'
import { refusalMessage } from './refusalMessage'

describe('refusalMessage', () => {
  it('says a refused code is not valid without repeating it', () => {
    expect(refusalMessage({ kind: 'wrong-code' })).toBe('That code is not valid.')
  })

  it('rounds the wait up to whole minutes and writes one minute in the singular', () => {
    expect(refusalMessage({ kind: 'locked', retryAfterSeconds: 60 })).toBe(
      'Too many attempts. Try again in 1 minute.',
    )
    expect(refusalMessage({ kind: 'locked', retryAfterSeconds: 61 })).toBe(
      'Too many attempts. Try again in 2 minutes.',
    )
  })

  it('asks to try again later when the demo cannot be reached', () => {
    expect(refusalMessage({ kind: 'failed' })).toBe(
      'The demo could not be reached. Try again in a moment.',
    )
  })
})
