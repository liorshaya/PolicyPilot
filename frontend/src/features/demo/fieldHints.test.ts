// @requirement FR-23
import { describe, expect, it } from 'vitest'
import { lendingRuleSet } from '../../test/fixtures/lending'
import { fieldHints } from './fieldHints'

// Document 4, Prompt 1: Author, Field hints. The expected text is written out by hand from that sentence and the
// lending fixture's fields, the same text the backend's FieldHintsTest expects
describe('fieldHints', () => {
  it("lists the lending policy's nine inputs, the enum with its values, and neither derived field", () => {
    expect(fieldHints(lendingRuleSet.fields)).toBe(
      [
        'The application supplies these inputs:',
        '- age (integer, years)',
        '- requested_amount (number, ILS)',
        '- term_months (integer, months)',
        '- employment_type (enum: salaried, self_employed, retired, unemployed)',
        '- employment_months (integer, months)',
        '- monthly_income (number, ILS)',
        '- existing_monthly_debt (number, ILS)',
        '- credit_events_24m (integer)',
        '- has_guarantor (boolean)',
      ].join('\n'),
    )
  })
})
