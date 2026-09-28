import { describe, expect, it } from 'vitest'
import type { Aggregates } from '../../api/types'
import { engineTime, outcomeCounts, percent } from './outcomes'

/**
 * The dashboard's arithmetic (Document 2, statistics; Document 6, Frontend Test Design: "the dashboard
 * aggregation"). The expected values are the aggregates of the 200 seeded cases as the engine reports them.
 */

// fixtures/policies/consumer-lending/cases-expected.json: 113 approved, 60 rejected, 27 referred, no errors,
// and the flags of those cases: STABLE_INCOME_MANUAL_CHECK on 113 of them and INCOME_NEAR_MINIMUM on 6
const seeded: Aggregates = {
  outcomes: { approve: 113, reject: 60, refer: 27 },
  errors: 0,
  flagCounts: { STABLE_INCOME_MANUAL_CHECK: 113, INCOME_NEAR_MINIMUM: 6 },
  topDecidingRules: [
    { ruleId: 'R-900', count: 113 },
    { ruleId: 'R-330', count: 11 },
  ],
  decisions: 200,
}

describe('outcomeCounts', () => {
  it('keeps the three outcomes in the order the figures read them, then the errors', () => {
    // the spec, section 09: Approved, Manual review, Declined, and the errors as a footnote
    expect(outcomeCounts(seeded).map((count) => count.outcome)).toEqual([
      'approve',
      'refer',
      'reject',
      'error',
    ])
  })

  it('counts the 200 seeded cases as the engine decided them', () => {
    const counts = outcomeCounts(seeded)

    expect(counts.map((count) => count.count)).toEqual([113, 27, 60, 0])
    expect(counts.reduce((sum, count) => sum + count.count, 0)).toBe(seeded.decisions)
  })

  it('states each share of all the decisions', () => {
    const counts = outcomeCounts(seeded)

    expect(counts[0]?.share).toBeCloseTo(0.565, 3)
    expect(counts[1]?.share).toBeCloseTo(0.135, 3)
    expect(counts[2]?.share).toBeCloseTo(0.3, 3)
  })

  it('reads an outcome the engine did not report as none of it', () => {
    const counts = outcomeCounts({ ...seeded, outcomes: { approve: 200 } })

    expect(counts.map((count) => count.count)).toEqual([200, 0, 0, 0])
  })

  it('shows no share at all when nothing has been decided', () => {
    const counts = outcomeCounts({
      outcomes: {},
      errors: 0,
      topDecidingRules: [],
      decisions: 0,
      flagCounts: {},
    })

    expect(counts.every((count) => count.count === 0 && count.share === 0)).toBe(true)
  })

  it('counts an evaluation error as its own outcome', () => {
    const counts = outcomeCounts({ ...seeded, errors: 2, decisions: 202 })

    expect(counts[3]).toEqual({ outcome: 'error', count: 2, share: 2 / 202 })
  })
})

describe('percent and engine time', () => {
  it('writes a share with one decimal, as the figures do', () => {
    // the spec, section 09: "113 56.5%", "27 13.5%", "60 30.0%"
    expect(percent(113 / 200)).toBe('56.5%')
    expect(percent(27 / 200)).toBe('13.5%')
    expect(percent(60 / 200)).toBe('30.0%')
    expect(percent(0)).toBe('0.0%')
    expect(percent(1)).toBe('100.0%')
  })

  // The engine decides one lending case in 4 to 113 microseconds on the live site (2026-09-22); one decimal of a
  // millisecond showed most of them as "0.0 ms"
  it('writes a decision under a millisecond in whole microseconds, never as 0.0 ms', () => {
    expect(engineTime(4)).toBe('4 µs')
    expect(engineTime(412)).toBe('412 µs')
    expect(engineTime(999)).toBe('999 µs')
  })

  it('writes a decision of a millisecond or more in milliseconds with one decimal', () => {
    expect(engineTime(1000)).toBe('1.0 ms')
    expect(engineTime(76_000)).toBe('76.0 ms')
  })
})
