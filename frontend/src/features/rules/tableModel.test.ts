import { describe, expect, it } from 'vitest'
import { lendingRuleSet } from '../../test/fixtures/lending'
import type { Rule } from '../../api/types'
import { actionText, bandOf, columnsOf, leavesOf, rowsOf, withLeaf } from './tableModel'

/**
 * The decision table as a view of the document (Document 3, Decision Table Rendering and Recommended priority
 * bands; Document 6, Frontend Test Design: "the table editing reducer"). The expected values are the committed
 * lending rule set and the bands of Document 3, never a rendering of this code.
 */

function rule(id: string): Rule {
  const found = lendingRuleSet.rules.find((candidate) => candidate.id === id)
  if (!found) {
    throw new Error(`the fixture has no rule ${id}`)
  }
  return found
}

describe('bandOf', () => {
  // Document 3, Recommended priority bands: 1-99, 100-199, 200-299, 300-399, 400-899, 900+
  it.each([
    [10, 'Derivations'],
    [99, 'Derivations'],
    [100, 'Eligibility'],
    [199, 'Eligibility'],
    [200, 'Affordability and risk'],
    [300, 'Referral'],
    [400, 'Advisory'],
    [899, 'Advisory'],
    [900, 'Approval'],
  ])('puts priority %i in the %s band', (priority, band) => {
    expect(bandOf(priority)).toBe(band)
  })
})

describe('actionText', () => {
  it('writes the three decisions in the words the interface uses', () => {
    expect(actionText(rule('R-100'))).toBe('Decline')
    expect(actionText(rule('R-330'))).toBe('Manual review')
    expect(actionText(rule('R-900'))).toBe('Approve')
  })

  it('writes a derivation as the field it sets', () => {
    expect(actionText(rule('R-010'))).toBe('set monthly_installment')
    expect(actionText(rule('R-020'))).toBe('set debt_to_income')
  })

  it('writes a candidate decision and a flag as Document 3 names them', () => {
    expect(
      actionText({
        ...rule('R-900'),
        actions: [{ type: 'decide', outcome: 'approve', terminal: false }],
      }),
    ).toBe('Approve (candidate)')
    expect(
      actionText({
        ...rule('R-900'),
        actions: [{ type: 'flag', code: 'STABLE_INCOME_MANUAL_CHECK' }],
      }),
    ).toBe('flag STABLE_INCOME_MANUAL_CHECK')
    // an action row with neither an outcome nor a field still has to say something
    expect(actionText({ ...rule('R-900'), actions: [{ type: 'decide' }, { type: 'set' }] })).toBe(
      'Manual review · set ',
    )
  })
})

describe('leavesOf', () => {
  it('reads one comparison, a flat list of them, and refuses a tree it cannot lay out', () => {
    expect(leavesOf(rule('R-100').condition)).toEqual({
      leaves: [{ field: 'age', op: 'lt', value: 21 }],
      flat: true,
    })
    expect(leavesOf(rule('R-110').condition).leaves).toHaveLength(2)
    expect(leavesOf(rule('R-110').condition).flat).toBe(true)
    // {"always": true} says nothing about any field, so the row has no cells
    expect(leavesOf(rule('R-900').condition)).toEqual({ leaves: [], flat: false })
    expect(leavesOf({ all: [{ any: [] }] }).flat).toBe(false)
  })
})

describe('columnsOf', () => {
  it('keeps the fields the rules use, in the order the document declares them', () => {
    const columns = columnsOf(lendingRuleSet).map((column) => column.name)
    const declared = lendingRuleSet.fields.map((field) => field.name)

    expect(columns).toEqual(declared.filter((name) => columns.includes(name)))
    expect(columns).toContain('debt_to_income')
    expect(columns).toContain('monthly_installment')
  })
})

describe('rowsOf', () => {
  it('orders the rows by priority, as the engine evaluates them', () => {
    const priorities = rowsOf(lendingRuleSet).map((row) => row.rule.priority)

    expect(priorities).toEqual([...priorities].sort((left, right) => left - right))
    expect(rowsOf(lendingRuleSet)[0]?.rule.id).toBe('R-010')
  })

  it('renders every comparison of a rule into the column of its field', () => {
    const row = rowsOf(lendingRuleSet).find((candidate) => candidate.rule.id === 'R-330')

    expect(row?.cells.get('credit_events_24m')?.text).toBe('= 1')
    expect(row?.cells.get('has_guarantor')?.text).toBe('= false')
    expect(row?.band).toBe('Referral')
    expect(row?.action).toBe('Manual review')
  })

  it('joins two comparisons on one field into one cell and locks it', () => {
    const document = {
      ...lendingRuleSet,
      rules: [
        {
          ...rule('R-100'),
          condition: {
            all: [
              { field: 'age', op: 'gte', value: 21 },
              { field: 'age', op: 'lte', value: 70 },
            ],
          },
        },
      ],
    }

    const cell = rowsOf(document)[0]?.cells.get('age')
    expect(cell?.text).toBe('≥ 21 years, ≤ 70 years')
    expect(cell?.editable).toBe(false)
  })
})

describe('withLeaf', () => {
  it('replaces the edited comparison and leaves the rest of the document untouched', () => {
    const before = rule('R-100')

    const edited = withLeaf(
      lendingRuleSet,
      'R-100',
      { field: 'age', op: 'lt', value: 21 },
      { field: 'age', op: 'lt', value: 23 },
    )

    expect(edited.rules.find((one) => one.id === 'R-100')?.condition).toEqual({
      field: 'age',
      op: 'lt',
      value: 23,
    })
    expect(before.condition).toEqual({ field: 'age', op: 'lt', value: 21 })
    expect({ ...edited, rules: edited.rules.filter((one) => one.id !== 'R-100') }).toEqual({
      ...lendingRuleSet,
      rules: lendingRuleSet.rules.filter((one) => one.id !== 'R-100'),
    })
  })

  it('replaces one comparison inside a list and keeps its siblings', () => {
    const edited = withLeaf(
      lendingRuleSet,
      'R-110',
      { field: 'age', op: 'gt', value: 70 },
      { field: 'age', op: 'gt', value: 75 },
    )

    expect(edited.rules.find((one) => one.id === 'R-110')?.condition).toEqual({
      all: [
        { field: 'age', op: 'gt', value: 75 },
        { field: 'employment_type', op: 'ne', value: 'retired' },
      ],
    })
  })

  it('changes nothing when the comparison it was given is not in the rule', () => {
    const edited = withLeaf(
      lendingRuleSet,
      'R-900',
      { field: 'age', op: 'gt', value: 70 },
      { field: 'age', op: 'gt', value: 75 },
    )

    expect(edited).toEqual(lendingRuleSet)
  })
})
