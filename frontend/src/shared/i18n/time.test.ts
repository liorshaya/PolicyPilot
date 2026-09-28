import { describe, expect, it } from 'vitest'
import { dateTimeOf, timeOf } from './time'

/**
 * When something happened, as the Register writes it (the spec, sections 04 and 09: "10:14:07", "2026-09-22 14:02"):
 * the time of day, and the date with the time, on the 24-hour clock. The tests run in UTC (src/test/setup.ts), so the
 * text does not depend on the machine's time zone; the product shows the reader's own.
 */
describe('timeOf and dateTimeOf', () => {
  it('writes the time of day on the 24-hour clock, with the seconds when asked', () => {
    expect(timeOf('2026-09-24T14:02:00Z')).toBe('14:02')
    expect(timeOf('2026-09-23T09:05:30Z')).toBe('09:05')
    // the spec's Cases screen, section 10: the trace's provenance ends in "10:14:07"
    expect(timeOf('2026-09-23T10:14:07Z', true)).toBe('10:14:07')
  })

  it('writes the date and the time, with the seconds when asked', () => {
    expect(dateTimeOf('2026-09-22T14:02:00Z')).toBe('2026-09-22 14:02')
    expect(dateTimeOf('2026-09-23T10:14:07Z', true)).toBe('2026-09-23 10:14:07')
  })
})
