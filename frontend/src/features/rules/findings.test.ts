import { describe, expect, it } from 'vitest'
import type { Finding, Review, ReviewFinding } from '../../api/types'
import {
  markOf,
  needs,
  publishBlockers,
  publishGates,
  reviewCounts,
  reviewStatusLine,
  tableCounts,
  tableMarks,
  underlinedCells,
} from './findings'

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

/** A finding of the validator (Document 3, Static Validation), anchored to rules and fields by the API. */
function validated(overrides: Partial<Finding>): Finding {
  return {
    code: 'DSL-311',
    severity: 'warning',
    path: '/rules/12',
    message: 'the rule is unreachable',
    ruleIds: ['R-330'],
    fieldNames: [],
    ...overrides,
  }
}

/**
 * The decision table's marks (the Register spec, section 07: "a 10px mark in a 20px gutter (square blocks publishing,
 * triangle warns) and underline the cell they name"; Document 3, Layout: "validation and reviewer findings anchored to
 * the rule").
 */
describe('tableMarks', () => {
  it("puts a finding's mark on every rule it names, by what the publish gate does with its kind", () => {
    const conflict = finding({
      id: 'F-2',
      kind: 'conflict',
      severity: 'error',
      ruleIds: ['R-110', 'R-115'],
      blocking: true,
    })
    const planted = finding({ id: 'F-5', kind: 'injection', ruleIds: ['R-420'], blocking: true })

    const marks = tableMarks(review([finding({}), conflict, planted]), [])

    expect(marks.get('R-110')).toStrictEqual([{ mark: 'error', label: 'F-2 Conflict' }])
    expect(marks.get('R-115')).toStrictEqual([{ mark: 'error', label: 'F-2 Conflict' }])
    expect(marks.get('R-420')).toStrictEqual([
      { mark: 'warning', label: 'F-1 Ambiguity' },
      { mark: 'injection', label: 'F-5 Instruction in the text' },
    ])
    expect(marks.has('R-100')).toBe(false)
  })

  it("marks the validator's findings by their severity, and leaves an acknowledged finding out", () => {
    const acknowledged = finding({
      id: 'F-2',
      kind: 'conflict',
      severity: 'error',
      ruleIds: ['R-110'],
      blocking: false,
      acknowledgement: { note: 'R-110 is narrowed to non-retirees', at: '2026-09-24T09:00:00Z' },
    })

    const marks = tableMarks(review([acknowledged]), [
      validated({}),
      validated({
        code: 'DSL-201',
        severity: 'error',
        message: 'unknown field',
        ruleIds: ['R-120'],
      }),
    ])

    expect(marks.has('R-110')).toBe(false)
    expect(marks.get('R-330')).toStrictEqual([
      { mark: 'warning', label: 'DSL-311 the rule is unreachable' },
    ])
    expect(marks.get('R-120')).toStrictEqual([{ mark: 'error', label: 'DSL-201 unknown field' }])
  })
})

describe('tableCounts', () => {
  it('counts the findings that block publishing and the open ones that warn, once each', () => {
    const conflict = finding({
      id: 'F-2',
      kind: 'conflict',
      severity: 'error',
      ruleIds: ['R-110', 'R-115'],
      blocking: true,
    })
    const gap = finding({ id: 'F-4', kind: 'gap', ruleIds: [], blocking: true })
    const acknowledged = finding({
      id: 'F-6',
      kind: 'unsupported',
      severity: 'error',
      blocking: false,
      acknowledgement: { note: 'the paragraph is implied', at: '2026-09-24T09:00:00Z' },
    })

    expect(
      tableCounts(review([finding({}), conflict, gap, acknowledged]), [
        validated({}),
        validated({ code: 'DSL-201', severity: 'error' }),
      ]),
    ).toStrictEqual({ block: 3, warn: 2 })
    expect(tableCounts(undefined, [])).toStrictEqual({ block: 0, warn: 0 })
  })
})

describe('underlinedCells', () => {
  it('underlines the cells a finding names by rule and field, an error over a warning', () => {
    const cells = underlinedCells([
      validated({ ruleIds: ['R-100'], fieldNames: ['age'] }),
      validated({
        code: 'DSL-201',
        severity: 'error',
        ruleIds: ['R-100', 'R-110'],
        fieldNames: ['age'],
      }),
      validated({ ruleIds: ['R-330'], fieldNames: [] }),
    ])

    expect(cells.get('R-100')?.get('age')).toBe('err')
    expect(cells.get('R-110')?.get('age')).toBe('err')
    expect(cells.has('R-330')).toBe(false)
    expect(
      underlinedCells([validated({ ruleIds: ['R-150'], fieldNames: ['employment_months'] })])
        .get('R-150')
        ?.get('employment_months'),
    ).toBe('warn')
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

/**
 * The review as the Register reads it (the spec, section 09, "The review"; Document 9, phase 3): the mark of each kind by
 * what the publish gate does with it, the head's counts, the lifecycle sentence and the four gates of the publish box.
 */
describe('markOf', () => {
  it('draws a square for what must be acknowledged, a bar for a planted instruction, a triangle for what may stay open', () => {
    expect(markOf(finding({ kind: 'conflict' }))).toBe('square')
    expect(markOf(finding({ kind: 'unsupported' }))).toBe('square')
    expect(markOf(finding({ kind: 'gap' }))).toBe('square')
    expect(markOf(finding({ kind: 'injection' }))).toBe('bar')
    expect(markOf(finding({ kind: 'ambiguity' }))).toBe('triangle')
    expect(markOf(finding({ kind: 'duplicate' }))).toBe('triangle')
  })
})

/** SF-1 to SF-5 of fixtures/eval/policies/consumer-lending/seeded.findings.json, the ambiguity acknowledged. */
const seeded = review(
  [
    finding({
      id: 'F-1',
      acknowledgement: { note: 'נומינלית, לפי הנוהג בבנק', at: '2026-09-24T14:02:00Z' },
    }),
    finding({
      id: 'F-2',
      kind: 'conflict',
      severity: 'error',
      ruleIds: ['R-110', 'R-115'],
      blocking: true,
    }),
    finding({
      id: 'F-3',
      kind: 'unsupported',
      severity: 'error',
      ruleIds: ['R-170'],
      blocking: true,
    }),
    finding({ id: 'F-4', kind: 'gap', ruleIds: ['R-160'], blocking: true }),
    finding({ id: 'F-5', kind: 'duplicate', ruleIds: ['R-100'] }),
  ],
  'DONE',
)

describe('reviewCounts', () => {
  it('counts the findings, those that block publishing and those a person acknowledged', () => {
    expect(reviewCounts(seeded)).toStrictEqual({ findings: 5, blocking: 3, acknowledged: 1 })
  })
})

describe('reviewStatusLine', () => {
  it("says the review's state in the product's own sentences", () => {
    // Document 9, phase 3, and the spec: "Not reviewed yet." · "The review could not run." · the stale sentence
    expect(reviewStatusLine(undefined)).toBe('Not reviewed yet.')
    expect(reviewStatusLine(review([], 'FAILED'))).toBe('The review could not run.')
    expect(reviewStatusLine(review([], 'STALE'))).toBe(
      'The draft was edited after its review; run the review again.',
    )
  })

  it('says what a done review was read against, with no time, which a review does not carry', () => {
    // the owner's answer of 2026-09-28 to phase 3's third question
    expect(
      reviewStatusLine({ ...seeded, coverage: { '1': ['R-100'], '4': ['R-170'], '9': ['R-900'] } }),
    ).toBe('Reviewed against ¶ 1–9. The draft has not changed since.')
    expect(reviewStatusLine(seeded)).toBe(
      'Reviewed against the policy. The draft has not changed since.',
    )
  })
})

describe('publishGates', () => {
  it('lists the four gates of a draft with their facts', () => {
    const gates = publishGates(
      { status: 'DRAFT', versionNo: 2 },
      { ...seeded, coverage: { '1': [], '9': [] } },
      [],
    )

    // the spec, section 09, the publish box: only a draft; valid; reviewed and not edited since; acknowledged n of m
    expect(gates).toStrictEqual([
      { label: 'Only a draft is published', state: 'ok', fact: 'Draft v2' },
      { label: 'Schema and semantics valid', state: 'ok', fact: '0 problems' },
      { label: 'Reviewed, and not edited since', state: 'ok', fact: '¶ 1–9' },
      { label: 'Blocking findings acknowledged', state: 'fail', fact: '0 of 3' },
    ])
  })

  it('waits on a stale review, and counts the problems of the validator', () => {
    const gates = publishGates({ status: 'DRAFT', versionNo: 2 }, review([], 'STALE'), [
      validated({ severity: 'error' }),
      validated({ code: 'DSL-201', severity: 'error' }),
      validated({}),
    ])

    expect(gates[1]).toStrictEqual({
      label: 'Schema and semantics valid',
      state: 'fail',
      fact: '2 problems',
    })
    expect(gates[2]).toStrictEqual({
      label: 'Reviewed, and not edited since',
      state: 'wait',
      fact: 'edited after its review',
    })
  })

  it('fails the first gate for a published version, and the third for a draft never reviewed', () => {
    const gates = publishGates({ status: 'PUBLISHED', versionNo: 1 }, undefined, [])

    expect(gates[0]).toStrictEqual({
      label: 'Only a draft is published',
      state: 'fail',
      fact: 'Published v1',
    })
    expect(gates[2]).toStrictEqual({
      label: 'Reviewed, and not edited since',
      state: 'fail',
      fact: 'not reviewed',
    })
    expect(gates[3]).toStrictEqual({
      label: 'Blocking findings acknowledged',
      state: 'ok',
      fact: '0 of 0',
    })
  })
})
