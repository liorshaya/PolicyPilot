import { describe, expect, it } from 'vitest'
import { lendingRuleSet } from '../../test/fixtures/lending'
import type { FieldSchema, Rule } from '../../api/types'
import {
  expressionText,
  isEditable,
  literalText,
  parseCell,
  renderCell,
  type Leaf,
} from './cellGrammar'

/**
 * The cell grammar (Document 3, Decision Table Rendering; Document 6, Frontend Test Design: "cell grammar round-trip
 * with Hebrew labels", 100% coverage). Every leaf of the committed lending rule set is rendered and read back, so
 * the table is proven to be a lossless view of the document the engine runs.
 */

const fields = new Map(lendingRuleSet.fields.map((field) => [field.name, field]))

function field(name: string): FieldSchema {
  const found = fields.get(name)
  if (!found) {
    throw new Error(`the fixture has no field ${name}`)
  }
  return found
}

/** Every single-field leaf of the committed rule set, in document order. */
function leavesOf(rule: Rule): Leaf[] {
  const condition = rule.condition as Record<string, unknown>
  if (Array.isArray(condition.all)) {
    return (condition.all as Record<string, unknown>[])
      .filter((child) => typeof child.field === 'string')
      .map((child) => child as unknown as Leaf)
  }
  return typeof condition.field === 'string' ? [condition as unknown as Leaf] : []
}

describe('renderCell', () => {
  it.each([
    [{ field: 'age', op: 'lt', value: 21 }, '< 21 years'],
    [{ field: 'age', op: 'gte', value: 21 }, '≥ 21 years'],
    [{ field: 'age', op: 'eq', value: 21 }, '= 21 years'],
    [{ field: 'age', op: 'ne', value: 21 }, '≠ 21 years'],
    [{ field: 'age', op: 'lte', value: 70 }, '≤ 70 years'],
    [{ field: 'age', op: 'between', value: [21, 70] }, '[21 years .. 70 years]'],
    [
      { field: 'requested_amount', op: 'between', value: [10000, 150000] },
      '[10,000 ILS .. 150,000 ILS]',
    ],
    [
      { field: 'employment_type', op: 'in', value: ['salaried', 'self_employed'] },
      '∈ {salaried, self_employed}',
    ],
    [{ field: 'employment_type', op: 'not_in', value: ['retired'] }, '∉ {retired}'],
    [{ field: 'employment_months', op: 'absent' }, 'absent'],
    [{ field: 'employment_months', op: 'present' }, 'present'],
    [{ field: 'has_guarantor', op: 'eq', value: false }, '= false'],
  ])('writes %o as its cell', (leaf, expected) => {
    expect(renderCell(leaf as Leaf, fields.get((leaf as Leaf).field))).toBe(expected)
  })

  it('writes a regular expression between slashes', () => {
    expect(renderCell({ field: 'iban', op: 'matches', value: '^IL[0-9]{2}' })).toBe(
      '~ /^IL[0-9]{2}/',
    )
  })

  it('writes an expression operand in its infix form, as Document 3 does', () => {
    const rule = lendingRuleSet.rules.find((candidate) => candidate.id === 'R-116')
    const leaf = leavesOf(rule!).find((candidate) => typeof candidate.value === 'object')

    expect(renderCell(leaf!, field('age'))).toBe('≥ (78 − (term_months / 12))')
  })

  it('writes an operator it does not know as its own name', () => {
    expect(renderCell({ field: 'age', op: 'unknown_op', value: 3 }, field('age'))).toBe(
      'unknown_op 3 years',
    )
  })
})

describe('parseCell', () => {
  it('reads back every leaf of the committed lending rule set', () => {
    const editable = lendingRuleSet.rules.flatMap(leavesOf).filter(isEditable)

    expect(editable.length).toBeGreaterThan(10)
    for (const leaf of editable) {
      const column = field(leaf.field)
      const parsed = parseCell(renderCell(leaf, column), column)
      expect(parsed.ok, `${leaf.field} ${leaf.op}`).toBe(true)
      if (parsed.ok) {
        expect(parsed.leaf).toEqual({
          field: leaf.field,
          op: leaf.op,
          ...(leaf.value === undefined ? {} : { value: leaf.value }),
        })
      }
    }
  })

  it('reads a number back without its grouping and its unit', () => {
    const parsed = parseCell('≥ 10,000 ILS', field('requested_amount'))

    expect(parsed).toEqual({
      ok: true,
      leaf: { field: 'requested_amount', op: 'gte', value: 10000 },
    })
  })

  it('reads a boolean, a range, a set and a pattern', () => {
    expect(parseCell('= false', field('has_guarantor'))).toEqual({
      ok: true,
      leaf: { field: 'has_guarantor', op: 'eq', value: false },
    })
    expect(parseCell('[12 .. 84]', field('term_months'))).toEqual({
      ok: true,
      leaf: { field: 'term_months', op: 'between', value: [12, 84] },
    })
    expect(parseCell('∉ {retired}', field('employment_type'))).toEqual({
      ok: true,
      leaf: { field: 'employment_type', op: 'not_in', value: ['retired'] },
    })
    expect(parseCell('~ /^IL[0-9]{2}/', field('employment_type'))).toEqual({
      ok: true,
      leaf: { field: 'employment_type', op: 'matches', value: '^IL[0-9]{2}' },
    })
    expect(parseCell('absent', field('employment_months'))).toEqual({
      ok: true,
      leaf: { field: 'employment_months', op: 'absent' },
    })
  })

  // Document 3: an edit that is not a comparison keeps the cell in an error state and blocks publishing
  it.each([
    ['', /empty/],
    ['   ', /empty/],
    ['≥ not a number', /comparison/],
    ['[a .. b]', /range/],
    ['∈ {}', /set/],
    ['more than 21', /Write =/],
  ])('refuses %j with a message that says what a cell may hold', (text, expected) => {
    const parsed = parseCell(text, field('age'))

    expect(parsed.ok).toBe(false)
    if (!parsed.ok) {
      expect(parsed.problem).toMatch(expected)
    }
  })

  it('refuses a fraction where the field is an integer', () => {
    expect(parseCell('= 2.5', field('credit_events_24m')).ok).toBe(false)
  })

  it('reads both boolean values and refuses a word that is neither', () => {
    expect(parseCell('= true', field('has_guarantor'))).toEqual({
      ok: true,
      leaf: { field: 'has_guarantor', op: 'eq', value: true },
    })
    expect(parseCell('= false', field('has_guarantor'))).toEqual({
      ok: true,
      leaf: { field: 'has_guarantor', op: 'eq', value: false },
    })
    expect(parseCell('= maybe', field('has_guarantor')).ok).toBe(false)
  })
})

describe('isEditable', () => {
  it('leaves an expression to the rule drawer', () => {
    const rule = lendingRuleSet.rules.find((candidate) => candidate.id === 'R-116')
    const expression = leavesOf(rule!).find((candidate) => typeof candidate.value === 'object')

    expect(isEditable(expression!)).toBe(false)
    expect(isEditable({ field: 'age', op: 'gte', value: 21 })).toBe(true)
  })
})

describe('literalText and expressionText', () => {
  it('writes a string as it is and a number with its unit', () => {
    expect(literalText('salaried')).toBe('salaried')
    expect(literalText(9500, field('monthly_income'))).toBe('9,500 ILS')
    expect(literalText(true)).toBe('true')
  })

  it('writes a function the infix form does not cover as a call', () => {
    expect(expressionText({ fn: 'round', args: [{ field: 'monthly_income' }, 2] })).toBe(
      'round(monthly_income, 2)',
    )
    expect(expressionText({ fn: 'add', args: [1, 2, 3] })).toBe('add(1, 2, 3)')
    expect(expressionText(null)).toBe('null')
    // a node that names a function but carries no arguments is not an expression the grammar can write
    expect(expressionText({ fn: 'add' })).toBe('[object Object]')
  })
})
