import { describe, expect, it } from 'vitest'
import { changeRequestName } from './names'

// @requirement FR-17

/**
 * A change request's name (the spec, sections 09 and 10: "CR-0001" on its chip, in the seal and in the audit log): its
 * number in the sandbox (Document 2, change_request.number, from 1), four digits at least.
 */
describe('changeRequestName', () => {
  it('writes the number with four digits at least', () => {
    expect(changeRequestName(1)).toBe('CR-0001')
    expect(changeRequestName(12)).toBe('CR-0012')
    expect(changeRequestName(12345)).toBe('CR-12345')
  })
})
