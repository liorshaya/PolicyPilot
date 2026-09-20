import { describe, expect, it } from 'vitest'
import type { Aggregates } from '../../api/types'
import { milliseconds, outcomeCounts, percent } from './outcomes'

/**
 * The dashboard's arithmetic (Document 2, statistics; Document 6, Frontend Test Design: "the dashboard
 * aggregation"). The expected values are the aggregates of the 200 seeded cases as the engine reports them.
 */

// fixtures/policies/consumer-lending/cases-expected.json: 113 approved, 60 rejected, 27 referred, no errors
const seeded: Aggregates = {
  outcomes: { approve: 113, reject: 60, refer: 27 },
  errors: 0,
  topDecidingRules: [
    { ruleId: 'R-900', count: 113 },
    { ruleId: 'R-330', count: 11 },
  ],
  decisions: 200,
}

describe('outcomeCounts', () => {
  it('keeps the three outcomes in the order the policy decides them, then the errors', () => {
    expect(outcomeCounts(seeded).map((count) => count.outcome)).toEqual([
      'approve',
      'reject',
      'refer',
      'error',
    ])
  })

  it('counts the 200 seeded cases as the engine decided them', () => {
    const counts = outcomeCounts(seeded)

    expect(counts.map((count) => count.count)).toEqual([113, 60, 27, 0])
    expect(counts.reduce((sum, count) => sum + count.count, 0)).toBe(seeded.decisions)
  })

  it('states each share of all the decisions', () => {
    const counts = outcomeCounts(seeded)

    expect(counts[0]?.share).toBeCloseTo(0.565, 3)
    expect(counts[1]?.share).toBeCloseTo(0.3, 3)
    expect(counts[2]?.share).toBeCloseTo(0.135, 3)
  })

  it('reads an outcome the engine did not report as none of it', () => {
    const counts = outcomeCounts({ ...seeded, outcomes: { approve: 200 } })

    expect(counts.map((count) => count.count)).toEqual([200, 0, 0, 0])
  })

  it('shows no share at all when nothing has been decided', () => {
    const counts = outcomeCounts({ outcomes: {}, errors: 0, topDecidingRules: [], decisions: 0 })

    expect(counts.every((count) => count.count === 0 && count.share === 0)).toBe(true)
  })

  it('counts an evaluation error as its own outcome', () => {
    const counts = outcomeCounts({ ...seeded, errors: 2, decisions: 202 })

    expect(counts[3]).toEqual({ outcome: 'error', count: 2, share: 2 / 202 })
  })
})

describe('percent and milliseconds', () => {
  it('writes a share as a whole percentage', () => {
    expect(percent(0.565)).toBe('57%')
    expect(percent(0)).toBe('0%')
    expect(percent(1)).toBe('100%')
  })

  it('writes the microseconds the engine reports as milliseconds', () => {
    expect(milliseconds(412)).toBe('0.4 ms')
    expect(milliseconds(76_000)).toBe('76.0 ms')
  })
})
