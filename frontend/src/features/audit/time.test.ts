import { describe, expect, it } from 'vitest'
import { formatInstant } from './time'

// @requirement FR-19

/**
 * When an audit entry was written, in one fixed form whatever the reader's locale or time zone (Document 6: no test
 * depends on the clock or the locale; the audit log is read by people in more than one place).
 */
describe('formatInstant', () => {
  it('writes an instant in UTC, to the minute', () => {
    expect(formatInstant('2026-09-27T09:12:00.482Z')).toBe('2026-09-27 09:12 UTC')
  })

  it('converts an offset to UTC, across the day it moves to', () => {
    expect(formatInstant('2026-09-28T01:30:00+03:00')).toBe('2026-09-27 22:30 UTC')
  })

  it('pads a single digit, and says so when the text is not an instant', () => {
    expect(formatInstant('2026-01-05T04:03:00Z')).toBe('2026-01-05 04:03 UTC')
    expect(formatInstant('yesterday')).toBe('yesterday')
  })
})
