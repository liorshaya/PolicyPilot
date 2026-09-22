import { describe, expect, it } from 'vitest'
import type { Review, ReviewFinding } from '../../api/types'
import { findingsByRule, needs, publishBlockers } from './findings'

/**
 * Expected values: Document 2, Flow 1 (what blocks a publish) and the acknowledge route (what each kind needs); the
 * anchors are those of fixtures/eval/policies/consumer-lending/seeded.findings.json.
 */

function finding(overrides: Partial<ReviewFinding>): ReviewFinding {
  return {
    id: 'F-1',
    kind: 'ambiguity',
    severity: 'warning',
    ruleIds: ['R-420'],
    paragraphIndexes: [4],
    message: 'הכנסה יציבה אינה מוגדרת',
    suggestion: 'להוסיף סימון לבדיקה ידנית',
    confidence: 0.8,
    blocking: false,
    ...overrides,
  }
}

function review(findings: ReviewFinding[], status: Review['status'] = 'DONE'): Review {
  return { status, promptVersion: 'v1', findings, coverage: {} }
}

describe('publishBlockers', () => {
  it('is empty for a done review whose blocking findings are all acknowledged', () => {
    expect(publishBlockers(review([finding({})]))).toEqual([])
  })

  it('names the open blocking findings by id', () => {
    const conflict = finding({ id: 'F-2', kind: 'conflict', severity: 'error', blocking: true })
    const gap = finding({ id: 'F-4', kind: 'gap', blocking: true })

    expect(publishBlockers(review([finding({}), conflict, gap]))).toEqual([
      '2 findings must be acknowledged: F-2, F-4.',
    ])
  })

  it('refuses a draft never reviewed, a failed review and a stale one', () => {
    expect(publishBlockers(undefined)).toEqual(['The draft has not been reviewed yet.'])
    expect(publishBlockers(review([], 'FAILED'))).toEqual([
      'The review could not run; run it again.',
    ])
    expect(publishBlockers(review([], 'STALE'))).toEqual([
      'The draft was edited after its review; run the review again.',
    ])
  })
})

describe('findingsByRule', () => {
  it('puts a finding on every rule it names', () => {
    const conflict = finding({ id: 'F-2', kind: 'conflict', ruleIds: ['R-110', 'R-115'] })

    const byRule = findingsByRule(review([finding({}), conflict]))

    expect(byRule.get('R-110')).toEqual([conflict])
    expect(byRule.get('R-115')).toEqual([conflict])
    expect(byRule.get('R-420')?.map((one) => one.id)).toEqual(['F-1'])
    expect(byRule.has('R-100')).toBe(false)
  })
})

describe('needs', () => {
  it('asks a gap for its resolution, an error for a note, and the rest for nothing', () => {
    expect(needs(finding({ kind: 'gap' }))).toBe('resolution')
    expect(needs(finding({ kind: 'conflict', severity: 'error' }))).toBe('note')
    expect(needs(finding({ kind: 'unsupported', severity: 'error' }))).toBe('note')
    expect(needs(finding({ kind: 'injection' }))).toBe('nothing')
    expect(needs(finding({ kind: 'duplicate' }))).toBe('nothing')
  })
})
