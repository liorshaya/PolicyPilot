import { describe, expect, it } from 'vitest'
import type { TraceStep } from '../../api/types'
import { actualText, expectedText, numbered, STEP_LABELS } from './trace'

/**
 * How a trace is read (Document 3, Trace format): what a rule expected is written in the same grammar the decision
 * table uses, what the case carried is written as it is, and the steps keep the engine's own order.
 */

describe('expectedText', () => {
  it.each([
    [{ field: 'monthly_income', op: 'lt', expected: 8000, actual: 9500, result: false }, '< 8,000'],
    [{ field: 'credit_events_24m', op: 'eq', expected: 1, actual: 1, result: true }, '= 1'],
    [{ field: 'has_guarantor', op: 'eq', expected: false, actual: false, result: true }, '= false'],
    [
      {
        field: 'employment_type',
        op: 'in',
        expected: ['salaried', 'self_employed'],
        actual: 'salaried',
        result: true,
      },
      '∈ {salaried, self_employed}',
    ],
    [{ field: 'employment_months', op: 'present', actual: null, result: false }, 'present'],
  ])('writes what the rule asked of %o', (comparison, expected) => {
    expect(expectedText(comparison)).toBe(expected)
  })
})

describe('actualText', () => {
  it('writes the value the case carried', () => {
    expect(actualText({ field: 'monthly_income', op: 'lt', actual: 9500, result: false })).toBe(
      '9,500',
    )
    expect(
      actualText({ field: 'employment_type', op: 'eq', actual: 'salaried', result: true }),
    ).toBe('salaried')
    expect(actualText({ field: 'has_guarantor', op: 'eq', actual: false, result: true })).toBe(
      'false',
    )
  })

  it('says a field the case did not carry is absent', () => {
    expect(
      actualText({ field: 'employment_months', op: 'present', actual: null, result: false }),
    ).toBe('absent')
    expect(
      actualText({ field: 'employment_months', op: 'present', actual: undefined, result: false }),
    ).toBe('absent')
  })
})

describe('numbered', () => {
  it('numbers the steps from one, in the order the engine walked them', () => {
    const steps: TraceStep[] = [
      { ruleId: 'R-010', label: 'a', priority: 10, status: 'fired' },
      { ruleId: 'R-170', label: 'b', priority: 170, status: 'not_fired' },
      { ruleId: 'R-900', label: 'c', priority: 900, status: 'skipped' },
    ]

    expect(numbered(steps)).toEqual([
      { no: 1, step: steps[0] },
      { no: 2, step: steps[1] },
      { no: 3, step: steps[2] },
    ])
    expect(numbered([])).toEqual([])
  })
})

describe('STEP_LABELS', () => {
  it('gives every status of Document 3 a word a person reads', () => {
    expect(STEP_LABELS).toEqual({
      fired: 'Matched',
      not_fired: 'Did not match',
      skipped: 'Not reached',
      disabled: 'Disabled',
      error: 'Evaluation error',
    })
  })
})
