import { describe, expect, it } from 'vitest'
import { lendingRuleSet } from '../../test/fixtures/lending'
import type { FieldSchema, Rule } from '../../api/types'
import {
  cellParts,
  cellText,
  conditionText,
  expressionText,
  isEditable,
  literalText,
  parseCell,
  renderCell,
  unitLabel,
  type Leaf,
} from './cellGrammar'

/**
 * The cell grammar (Document 3, Decision Table Rendering; Document 6, Frontend Test Design: "cell grammar round-trip
 * with Hebrew labels", 100% coverage). Every leaf of the committed lending rule set is rendered and read back, so
 * the table is proven to be a lossless view of the document the engine runs. The cells are Document 3's own examples
 * and the Register's (the spec, section 07): the operator and the operand, the unit left to the column's header.
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

/** The expression operand of R-116: 78 − term_months / 12 (Document 3, Conditions). */
function ageLimitOfR116(): Leaf {
  const rule = lendingRuleSet.rules.find((candidate) => candidate.id === 'R-116')
  const leaf = leavesOf(rule!).find((candidate) => typeof candidate.value === 'object')
  return leaf!
}

describe('cellParts and cellText', () => {
  // Document 3, the Layout table and the cell grammar: ≥ 21, ∈ {salaried, self_employed}, [10,000 .. 150,000], absent
  it.each([
    [{ field: 'age', op: 'lt', value: 21 }, '<', '21'],
    [{ field: 'age', op: 'gte', value: 21 }, '≥', '21'],
    [{ field: 'age', op: 'lte', value: 70 }, '≤', '70'],
    [{ field: 'age', op: 'gt', value: 70 }, '>', '70'],
    [{ field: 'credit_events_24m', op: 'eq', value: 1 }, '=', '1'],
    [
      { field: 'requested_amount', op: 'between', value: [10000, 150000] },
      undefined,
      '[10,000 .. 150,000]',
    ],
    [{ field: 'employment_months', op: 'absent' }, undefined, 'absent'],
    [{ field: 'employment_months', op: 'present' }, undefined, 'present'],
  ])('writes %o with the operator apart from the operand, and no unit', (leaf, op, value) => {
    const parts = cellParts(leaf)

    expect(parts?.op).toBe(op)
    expect(parts?.value).toBe(value)
    expect(parts?.token).toBe(false)
  })

  it('writes enum values and booleans as machine tokens, a set of them as one that may wrap', () => {
    // the spec, section 07: "enum values in mono"; R-110's ≠ retired, R-330's = false, R-310's ∈ {…}
    expect(cellParts({ field: 'employment_type', op: 'ne', value: 'retired' })).toMatchObject({
      op: '≠',
      value: 'retired',
      token: true,
      set: false,
    })
    expect(cellParts({ field: 'has_guarantor', op: 'eq', value: false })).toMatchObject({
      op: '=',
      value: 'false',
      token: true,
    })
    expect(
      cellParts({ field: 'employment_type', op: 'in', value: ['salaried', 'self_employed'] }),
    ).toMatchObject({ op: '∈', value: '{salaried, self_employed}', token: true, set: true })
    expect(cellParts({ field: 'employment_type', op: 'not_in', value: ['retired'] })).toMatchObject(
      {
        op: '∉',
        value: '{retired}',
      },
    )
  })

  it('writes an expression operand in its infix form and marks it as one', () => {
    // Document 3, the cell grammar: "≥ 78 − term_months / 12"
    expect(cellParts(ageLimitOfR116())).toMatchObject({
      op: '≥',
      value: '78 − term_months / 12',
      expression: true,
    })
  })

  // the spec, section 07: a not around one comparison shows in that field's column with the negated sign
  it('writes a negated range, equality and set with the negated sign', () => {
    const negated = { negated: true }

    expect(
      cellText(
        cellParts({ field: 'requested_amount', op: 'between', value: [10000, 150000] }, negated)!,
      ),
    ).toBe('∉ [10,000 .. 150,000]')
    expect(
      cellText(cellParts({ field: 'employment_type', op: 'eq', value: 'retired' }, negated)!),
    ).toBe('≠ retired')
    expect(
      cellText(
        cellParts({ field: 'employment_type', op: 'in', value: ['salaried', 'retired'] }, negated)!,
      ),
    ).toBe('∉ {salaried, retired}')
  })

  it('has no negated form for any other comparison', () => {
    expect(cellParts({ field: 'age', op: 'gt', value: 70 }, { negated: true })).toBeNull()
    expect(cellParts({ field: 'employment_months', op: 'absent' }, { negated: true })).toBeNull()
  })

  it('writes every number of a column in the column precision', () => {
    // the spec, section 07: R-200 reads "> 0.40" and R-320 "[0.35 .. 0.40]" beside it, one precision per column
    expect(
      cellParts({ field: 'debt_to_income', op: 'gt', value: 0.4 }, { precision: 2 })?.value,
    ).toBe('0.40')
    expect(
      cellParts(
        { field: 'debt_to_income', op: 'between', value: [0.35, 0.4] },
        {
          precision: 2,
        },
      )?.value,
    ).toBe('[0.35 .. 0.40]')
  })

  it('writes the operator and the operand on one line, a word or a range alone', () => {
    expect(cellText({ op: '<', value: '21', token: false, set: false, expression: false })).toBe(
      '< 21',
    )
    expect(cellText({ value: 'absent', token: false, set: false, expression: false })).toBe(
      'absent',
    )
  })
})

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
  ])(
    'writes %o with the unit of its field, for a view with no column to carry it',
    (leaf, expected) => {
      expect(renderCell(leaf as Leaf, fields.get((leaf as Leaf).field))).toBe(expected)
    },
  )

  it('writes a regular expression between slashes', () => {
    expect(renderCell({ field: 'iban', op: 'matches', value: '^IL[0-9]{2}' })).toBe(
      '~ /^IL[0-9]{2}/',
    )
  })

  it('writes an expression operand in its infix form, as Document 3 does', () => {
    expect(renderCell(ageLimitOfR116(), field('age'))).toBe('≥ 78 − term_months / 12')
  })

  it('writes an operator it does not know as its own name', () => {
    expect(renderCell({ field: 'age', op: 'unknown_op', value: 3 }, field('age'))).toBe(
      'unknown_op 3 years',
    )
  })
})

describe('parseCell', () => {
  it('reads back every leaf of the committed lending rule set from its cell', () => {
    const editable = lendingRuleSet.rules.flatMap(leavesOf).filter(isEditable)

    expect(editable.length).toBeGreaterThan(10)
    for (const leaf of editable) {
      const column = field(leaf.field)
      const parsed = parseCell(cellText(cellParts(leaf)!), column)
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

  it('reads a number back without its grouping, and a padded decimal as its value', () => {
    expect(parseCell('≥ 10,000', field('requested_amount'))).toEqual({
      ok: true,
      leaf: { field: 'requested_amount', op: 'gte', value: 10000 },
    })
    expect(parseCell('> 0.40', field('debt_to_income'))).toEqual({
      ok: true,
      leaf: { field: 'debt_to_income', op: 'gt', value: 0.4 },
    })
  })

  // the spec, section 07: the error bubble says what to write instead ("Write the number alone; the column is in ₪.")
  it('refuses a number written with its unit, and says the column carries it', () => {
    expect(parseCell('< 8,000 ₪', field('monthly_income'))).toEqual({
      ok: false,
      problem: 'Write the number alone; the column is in ₪.',
    })
    expect(parseCell('< 8,000 ILS', field('monthly_income'))).toEqual({
      ok: false,
      problem: 'Write the number alone; the column is in ₪.',
    })
    expect(parseCell('< 21 years', field('age'))).toEqual({
      ok: false,
      problem: 'Write the number alone; the column is in years.',
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
    expect(isEditable(ageLimitOfR116())).toBe(false)
    expect(isEditable({ field: 'age', op: 'gte', value: 21 })).toBe(true)
  })
})

describe('unitLabel', () => {
  it('writes the shekel sign for ILS and any other unit as the rule set names it', () => {
    // the spec, section 07: requested_amount ₪, age years
    expect(unitLabel('ILS')).toBe('₪')
    expect(unitLabel('years')).toBe('years')
  })
})

describe('literalText and expressionText', () => {
  it('writes a string as it is and a number with its unit', () => {
    expect(literalText('salaried')).toBe('salaried')
    expect(literalText(9500, field('monthly_income'))).toBe('9,500 ILS')
    expect(literalText(true)).toBe('true')
  })

  it('writes an expression without the brackets its operators do not need', () => {
    const term = { field: 'term_months' }
    // Document 3: 78 − term_months / 12, and R-020's (existing_monthly_debt + monthly_installment) / monthly_income
    expect(expressionText({ fn: 'sub', args: [78, { fn: 'div', args: [term, 12] }] })).toBe(
      '78 − term_months / 12',
    )
    expect(
      expressionText({
        fn: 'div',
        args: [
          {
            fn: 'add',
            args: [{ field: 'existing_monthly_debt' }, { field: 'monthly_installment' }],
          },
          { field: 'monthly_income' },
        ],
      }),
    ).toBe('(existing_monthly_debt + monthly_installment) / monthly_income')
    // a difference on the right of a difference keeps its brackets: a − (b − c) is not a − b − c
    expect(expressionText({ fn: 'sub', args: [1, { fn: 'sub', args: [2, 3] }] })).toBe(
      '1 − (2 − 3)',
    )
    expect(expressionText({ fn: 'mul', args: [{ fn: 'add', args: [1, 2] }, 3] })).toBe(
      '(1 + 2) × 3',
    )
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

/**
 * A whole condition on one line (Document 3, Decision Table Rendering, the Structure column: "a compact rendering of
 * any and not ... for example NOT [amount ∈ [10,000 .. 150,000]]"), for the diff view, where a rule is read whole. The
 * conditions are the lending rule set's own.
 */
describe('conditionText', () => {
  const conditionOf = (id: string): unknown =>
    lendingRuleSet.rules.find((candidate) => candidate.id === id)?.condition

  it('writes a leaf as its field and its cell', () => {
    expect(conditionText(conditionOf('R-170'), fields)).toBe('monthly_income < 8,000 ILS')
    expect(conditionText(conditionOf('R-410'), fields)).toBe(
      'monthly_income ∈ [8,000 ILS .. 9,000 ILS]',
    )
  })

  it('writes not as NOT around its child, and all as the leaves joined by AND', () => {
    expect(conditionText(conditionOf('R-120'), fields)).toBe(
      'NOT [requested_amount ∈ [10,000 ILS .. 150,000 ILS]]',
    )
    expect(conditionText(conditionOf('R-310'), fields)).toBe(
      'employment_type ∈ {salaried, self_employed} AND employment_months absent',
    )
  })

  it('writes an expression operand in its infix form', () => {
    expect(conditionText(conditionOf('R-116'), fields)).toBe(
      'employment_type = retired AND age ≥ 78 − term_months / 12',
    )
  })

  it('writes any as its children joined by OR, in brackets when it sits inside another', () => {
    const either = {
      any: [
        { field: 'credit_events_24m', op: 'gte', value: 2 },
        { field: 'has_guarantor', op: 'eq', value: false },
      ],
    }

    expect(conditionText(either, fields)).toBe('credit_events_24m ≥ 2 OR has_guarantor = false')
    expect(conditionText({ all: [{ field: 'age', op: 'gt', value: 70 }, either] }, fields)).toBe(
      'age > 70 years AND (credit_events_24m ≥ 2 OR has_guarantor = false)',
    )
  })

  it('writes the constant condition as always, and anything else as its JSON', () => {
    expect(conditionText({ always: true }, fields)).toBe('always')
    expect(conditionText({ unknown: 1 }, fields)).toBe('{"unknown":1}')
    expect(conditionText(null, fields)).toBe('null')
    expect(conditionText(undefined, fields)).toBe('undefined')
  })
})
