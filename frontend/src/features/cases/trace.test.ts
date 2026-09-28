import { describe, expect, it } from 'vitest'
import type { TraceStep } from '../../api/types'
import { sampleDecision } from '../../test/fixtures/lending'
import {
  actualText,
  collapsedCounts,
  effectText,
  expectedParts,
  expectedText,
  hitMap,
  hitMapLabel,
  noFlags,
  STEP_LABELS,
} from './trace'

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

/**
 * The trace's grouping (Document 9, phase 3: "trace.ts only groups"): the hit map, its words, the collapsed steps and
 * what an action did, every value the engine's. The expected texts are case 17's, fixtures/policies/consumer-lending/
 * sample-decision.json, the worked example of Document 3.
 */
describe('hitMap and hitMapLabel', () => {
  it('draws one cell per rule in the order the engine walked them, the deciding one marked', () => {
    const cells = hitMap(sampleDecision)

    expect(cells).toHaveLength(20)
    expect(cells.slice(0, 3)).toStrictEqual([
      { ruleId: 'R-010', status: 'fired', deciding: false },
      { ruleId: 'R-020', status: 'fired', deciding: false },
      { ruleId: 'R-100', status: 'not_fired', deciding: false },
    ])
    expect(cells.find((cell) => cell.deciding)).toStrictEqual({
      ruleId: 'R-330',
      status: 'fired',
      deciding: true,
    })
  })

  it('names the cells for a screen reader by how many took each status', () => {
    // Document 3: "17 rules were evaluated and 3 were skipped", 3 of them fired
    expect(hitMapLabel(hitMap(sampleDecision))).toBe(
      '20 rules: 3 matched, 14 did not match, 3 not reached',
    )
    expect(
      hitMapLabel([
        { ruleId: 'R-100', status: 'fired', deciding: true },
        { ruleId: 'R-310', status: 'disabled', deciding: false },
      ]),
    ).toBe('2 rules: 1 matched, 1 disabled')
  })
})

describe('collapsedCounts', () => {
  it('counts the rules that did not match and those not reached after the decision', () => {
    expect(collapsedCounts(sampleDecision)).toStrictEqual({ didNotMatch: 14, notReached: 3 })
  })
})

describe('effectText', () => {
  it('writes what an action did in the words of the engine', () => {
    const [r010, r020] = sampleDecision.trace
    const r330 = sampleDecision.trace.find((step) => step.ruleId === 'R-330')!

    expect(effectText(r010!.actions![0]!)).toBe('set monthly_installment · null → 1,493.1')
    expect(effectText(r020!.actions![0]!)).toBe('set debt_to_income · null → 0.2835')
    expect(effectText(r330.actions![0]!)).toBe('decide · Manual review · terminal')
    expect(effectText({ type: 'flag', code: 'INCOME_NEAR_MINIMUM' })).toBe(
      'flag INCOME_NEAR_MINIMUM',
    )
    expect(effectText({ type: 'decide', outcome: 'approve', terminal: false })).toBe(
      'decide · Approve · candidate',
    )
  })
})

describe('expectedParts', () => {
  it("writes a computed operand as its value, then the engine's own text of it", () => {
    const r116 = sampleDecision.trace.find((step) => step.ruleId === 'R-116')!

    // the trace records { value: 74, text: "(78 - (term_months / 12))" } (Document 3, R-116 in full)
    expect(expectedParts(r116.comparisons![1]!)).toStrictEqual({
      cond: '≥ 74',
      expression: '(78 - (term_months / 12))',
    })
    expect(expectedParts(r116.comparisons![0]!)).toStrictEqual({ cond: '= retired' })
  })
})

describe('noFlags', () => {
  it('says why a decision has no flags when it came before the advisory rules', () => {
    // case 17 was decided at R-330; R-410 and R-420, the advisory band (400–499), were not reached
    expect(noFlags(sampleDecision)).toBe('None: the decision came before the advisory rules.')
  })

  it('says only "None." when the advisory rules were reached and raised nothing', () => {
    const reached: TraceStep[] = sampleDecision.trace.map((step) =>
      step.status === 'skipped' ? { ...step, status: 'not_fired' } : step,
    )

    expect(noFlags({ ...sampleDecision, trace: reached })).toBe('None.')
  })
})
