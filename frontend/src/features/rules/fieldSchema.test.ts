import { describe, expect, it } from 'vitest'
import type { FieldSchema } from '../../api/types'
import { lendingRuleSet } from '../../test/fixtures/lending'
import { derivedBy, fieldSummary } from './fieldSchema'

// @requirement FR-6

/**
 * A field of the case schema as the Register writes it after its name (the spec, section 09, "Decide a case, and the
 * field schema"): its type, its unit, required or optional, and its domain; a derived field says which rule derives it.
 * Expected: the spec's two specimens, which are drawn from the lending rule set's fields
 * (fixtures/policies/consumer-lending/ruleset.v1.json).
 */
const field = (name: string): FieldSchema => lendingRuleSet.fields.find((one) => one.name === name)!

describe('fieldSummary', () => {
  it.each([
    ['age', 'integer · years · required · 0 to 120'],
    ['requested_amount', 'number · ₪ · required'],
    ['term_months', 'integer · months · required · at least 1'],
    ['employment_type', 'enum · required'],
    ['employment_months', 'integer · months · optional'],
    ['monthly_income', 'number · ₪ · required'],
    ['existing_monthly_debt', 'number · ₪ · required'],
    ['credit_events_24m', 'integer · required'],
    ['has_guarantor', 'boolean · optional, false when absent'],
  ])('writes %s as the spec does: %s', (name, expected) => {
    expect(fieldSummary(field(name))).toBe(expected)
  })

  it.each([
    ['monthly_installment', 'R-010', 'number · ₪ · derived by R-010'],
    ['debt_to_income', 'R-020', 'number · derived by R-020'],
  ])('writes the derived %s with the rule that derives it', (name, rule, expected) => {
    expect(fieldSummary(field(name), rule)).toBe(expected)
  })

  // Document 3, Field Schema: the exclusive bounds, which the lending fields do not use
  it('writes an exclusive bound as more than or less than, and a lone maximum as at most', () => {
    expect(fieldSummary({ name: 'ratio', type: 'number', exclusiveMinimum: 0, maximum: 1 })).toBe(
      'number · optional · more than 0, at most 1',
    )
    expect(fieldSummary({ name: 'score', type: 'integer', required: true, maximum: 999 })).toBe(
      'integer · required · at most 999',
    )
    expect(fieldSummary({ name: 'rate', type: 'number', exclusiveMaximum: 100 })).toBe(
      'number · optional · less than 100',
    )
  })
})

describe('derivedBy', () => {
  // Document 3: a derived field is set by a rule's `set` action; R-010 computes the installment, R-020 the ratio
  it('names the rule whose set action derives the field', () => {
    expect(derivedBy(lendingRuleSet, 'monthly_installment')).toBe('R-010')
    expect(derivedBy(lendingRuleSet, 'debt_to_income')).toBe('R-020')
  })

  it('names none for a field the case supplies', () => {
    expect(derivedBy(lendingRuleSet, 'age')).toBeUndefined()
  })
})
