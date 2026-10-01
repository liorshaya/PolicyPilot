import { describe, expect, it } from 'vitest'
import { lendingRuleSet } from '../../test/fixtures/lending'
import type { Diff, FieldSchema, Rule, RuleSetDocument } from '../../api/types'
import {
  actionText,
  bandOf,
  bandedRows,
  columnsOf,
  comparisonsOf,
  fieldTitle,
  fieldsOutOfView,
  headerUnit,
  pointedCell,
  rowsOf,
  sinceOf,
  tagsOf,
  withEnabled,
  withLeaf,
  withRequired,
} from './tableModel'

/**
 * The decision table as a view of the document (Document 3, Decision Table Rendering and Recommended priority
 * bands; Document 6, Frontend Test Design: "the table editing reducer"; the Register spec, section 07). The expected
 * values are the committed lending rule set, Document 3's examples and the spec's decision table, never a rendering of
 * this code.
 */

function rule(id: string): Rule {
  const found = lendingRuleSet.rules.find((candidate) => candidate.id === id)
  if (!found) {
    throw new Error(`the fixture has no rule ${id}`)
  }
  return found
}

function field(name: string): FieldSchema {
  const found = lendingRuleSet.fields.find((candidate) => candidate.name === name)
  if (!found) {
    throw new Error(`the fixture has no field ${name}`)
  }
  return found
}

/** The lending rule set with one rule in it, its condition replaced. */
function withCondition(condition: unknown): RuleSetDocument {
  return { ...lendingRuleSet, rules: [{ ...rule('R-100'), condition }] }
}

function cellOf(document: RuleSetDocument, ruleId: string, fieldName: string) {
  return rowsOf(document)
    .find((row) => row.rule.id === ruleId)
    ?.cells.get(fieldName)
}

describe('bandOf', () => {
  // Document 3, Recommended priority bands, row by row, and the spec's band rows: "Derivations 1–99"
  it.each([
    [1, 'Derivations', '1–99'],
    [99, 'Derivations', '1–99'],
    [100, 'Hard eligibility gates', '100–199'],
    [199, 'Hard eligibility gates', '100–199'],
    [200, 'Affordability and risk limits', '200–299'],
    [299, 'Affordability and risk limits', '200–299'],
    [300, 'Referral conditions', '300–399'],
    [399, 'Referral conditions', '300–399'],
    [400, 'Advisory', '400–499'],
    [499, 'Advisory', '400–499'],
    [900, 'Positive outcome', '900–999'],
    [999, 'Positive outcome', '900–999'],
  ])('puts priority %i in the %s band, %s', (priority, name, range) => {
    expect(bandOf(priority)).toStrictEqual({ name, range })
  })

  it('says so about a priority Document 3 gives no band, and gives it no range', () => {
    for (const priority of [0, 500, 899, 1000]) {
      expect(bandOf(priority)).toStrictEqual({ name: 'Outside the recommended bands' })
    }
  })
})

describe('actionText', () => {
  it("writes the three decisions in the words of a rule's action column", () => {
    // the spec's glossary: Approve · Decline · Manual review in a rule's Action column
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

describe('comparisonsOf', () => {
  it('reads one comparison, a flat list of them, a negated one, and refuses a tree it cannot lay out', () => {
    expect(comparisonsOf(rule('R-100').condition)).toEqual({
      comparisons: [{ leaf: { field: 'age', op: 'lt', value: 21 }, negated: false }],
      flat: true,
    })
    expect(comparisonsOf(rule('R-110').condition).comparisons).toHaveLength(2)
    expect(comparisonsOf(rule('R-110').condition).flat).toBe(true)
    // R-120 is a not around one range: shown in its column, never edited there
    expect(comparisonsOf(rule('R-120').condition)).toEqual({
      comparisons: [
        {
          leaf: { field: 'requested_amount', op: 'between', value: [10000, 150000] },
          negated: true,
        },
      ],
      flat: false,
    })
    // {"always": true} says nothing about any field, so the row has no cells
    expect(comparisonsOf(rule('R-900').condition)).toEqual({ comparisons: [], flat: false })
    expect(comparisonsOf({ all: [{ any: [] }] }).flat).toBe(false)
  })
})

describe('columnsOf', () => {
  it('lists the fields the rules compare, case fields in document order and derived fields last', () => {
    // Document 3: nine condition columns for the lending policy; the spec's header names them in this order
    expect(columnsOf(lendingRuleSet).map((column) => column.name)).toStrictEqual([
      'age',
      'requested_amount',
      'term_months',
      'employment_type',
      'employment_months',
      'monthly_income',
      'credit_events_24m',
      'has_guarantor',
      'debt_to_income',
    ])
  })

  it('puts a derived field after every case field, whatever order the document declares them in', () => {
    const document: RuleSetDocument = {
      ...lendingRuleSet,
      fields: [field('debt_to_income'), field('age')],
      rules: [
        {
          ...rule('R-100'),
          condition: {
            all: [
              { field: 'debt_to_income', op: 'gt', value: 0.4 },
              { field: 'age', op: 'lt', value: 21 },
            ],
          },
        },
      ],
    }

    expect(columnsOf(document).map((column) => column.name)).toStrictEqual([
      'age',
      'debt_to_income',
    ])
  })

  it('gives no column to a field a rule only sets or reads inside an expression', () => {
    const names = columnsOf(lendingRuleSet).map((column) => column.name)

    expect(names).not.toContain('monthly_installment')
    expect(names).not.toContain('existing_monthly_debt')
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
    expect(row?.band.name).toBe('Referral conditions')
    expect(row?.action).toBe('Manual review')
  })

  it("shows a not over one range in the field's column with the negated sign, read-only", () => {
    // the spec, section 07: ∉ [10,000 .. 150,000], "read-only, like an expression"
    const cell = cellOf(lendingRuleSet, 'R-120', 'requested_amount')

    expect(cell?.text).toBe('∉ [10,000 .. 150,000]')
    expect(cell?.editable).toBe(false)
    expect(cellOf(lendingRuleSet, 'R-130', 'term_months')?.text).toBe('∉ [12 .. 84]')
  })

  it('shows a not over one equality as ≠ and a not over a set as ∉, read-only', () => {
    const unequal = withCondition({ not: { field: 'employment_type', op: 'eq', value: 'retired' } })
    const outside = withCondition({
      not: { field: 'employment_type', op: 'in', value: ['salaried', 'retired'] },
    })

    expect(cellOf(unequal, 'R-100', 'employment_type')?.text).toBe('≠ retired')
    expect(cellOf(unequal, 'R-100', 'employment_type')?.editable).toBe(false)
    expect(cellOf(outside, 'R-100', 'employment_type')?.text).toBe('∉ {salaried, retired}')
  })

  it('keeps any other not out of the cells, for the margin to read whole', () => {
    expect(
      rowsOf(withCondition({ not: { field: 'age', op: 'gt', value: 70 } }))[0]?.cells.size,
    ).toBe(0)
    expect(
      rowsOf(
        withCondition({
          not: {
            all: [
              { field: 'age', op: 'gt', value: 70 },
              { field: 'employment_type', op: 'eq', value: 'retired' },
            ],
          },
        }),
      )[0]?.cells.size,
    ).toBe(0)
    // beside a comparison the table can show, the rest of the tree stays out, and nothing is editable
    const mixed = rowsOf(
      withCondition({
        all: [
          { field: 'employment_type', op: 'eq', value: 'retired' },
          { not: { field: 'age', op: 'gt', value: 70 } },
        ],
      }),
    )[0]
    expect([...mixed!.cells.keys()]).toStrictEqual(['employment_type'])
    expect(mixed?.cells.get('employment_type')?.editable).toBe(false)
  })

  it('marks an expression operand as one, and leaves it to the margin', () => {
    const cell = cellOf(lendingRuleSet, 'R-116', 'age')

    expect(cell?.parts[0]?.expression).toBe(true)
    expect(cell?.text).toBe('≥ 78 − term_months / 12')
    expect(cell?.editable).toBe(false)
  })

  it('writes the numbers of a column in one precision', () => {
    // the spec, section 07: debt_to_income reads "> 0.40" for R-200 and "[0.35 .. 0.40]" for R-320
    expect(cellOf(lendingRuleSet, 'R-200', 'debt_to_income')?.text).toBe('> 0.40')
    expect(cellOf(lendingRuleSet, 'R-320', 'debt_to_income')?.text).toBe('[0.35 .. 0.40]')
    expect(cellOf(lendingRuleSet, 'R-170', 'monthly_income')?.text).toBe('< 8,000')
  })

  it('joins two comparisons on one field into one cell and locks it', () => {
    const cell = cellOf(
      withCondition({
        all: [
          { field: 'age', op: 'gte', value: 21 },
          { field: 'age', op: 'lte', value: 70 },
        ],
      }),
      'R-100',
      'age',
    )

    expect(cell?.text).toBe('≥ 21, ≤ 70')
    expect(cell?.editable).toBe(false)
  })
})

describe('bandedRows', () => {
  it('groups the committed rule set into the bands it uses, in priority order', () => {
    expect(bandedRows(lendingRuleSet).map((band) => band.band.name)).toEqual([
      'Derivations',
      'Hard eligibility gates',
      'Affordability and risk limits',
      'Referral conditions',
      'Advisory',
      'Positive outcome',
    ])
    expect(bandedRows(lendingRuleSet).flatMap((band) => band.rows)).toHaveLength(
      lendingRuleSet.rules.length,
    )
  })

  it('opens a band again when the priorities leave it and come back', () => {
    const document = {
      ...lendingRuleSet,
      rules: [
        rule('R-100'),
        { ...rule('R-200'), priority: 250 },
        { ...rule('R-110'), priority: 260 },
      ],
    }

    // the engine's order is the priority order, so a band that is left and re-entered is shown twice
    expect(
      bandedRows(document).map((band) => `${band.band.name}:${String(band.rows.length)}`),
    ).toEqual(['Hard eligibility gates:1', 'Affordability and risk limits:2'])
  })

  it('keeps the rules of one tag, in their bands', () => {
    // Document 3, Rule attributes: tags are the free grouping the UI filter reads
    const bands = bandedRows(lendingRuleSet, 'credit_history')

    expect(bands.map((band) => band.band.name)).toEqual([
      'Affordability and risk limits',
      'Referral conditions',
    ])
    expect(bands.flatMap((band) => band.rows.map((row) => row.rule.id))).toEqual(['R-220', 'R-330'])
  })
})

describe('tagsOf', () => {
  it('lists every tag of the rule set once, in alphabetical order', () => {
    expect(tagsOf(lendingRuleSet)).toStrictEqual([
      'advisory',
      'affordability',
      'credit_history',
      'data_quality',
      'derivation',
      'eligibility',
      'manual_criteria',
      'outcome',
    ])
  })
})

describe('fieldTitle', () => {
  // the spec, section 07: the header's title, the DSL's Hebrew description first, then type, unit, values and paragraph
  it.each([
    ['age', 'גיל המבקש בעת הגשת הבקשה · integer, years, 0 to 120 · ¶ 1'],
    ['requested_amount', 'סכום ההלוואה המבוקש · number, ₪ · ¶ 2'],
    ['term_months', 'תקופת ההחזר בחודשים · integer, months, at least 1 · ¶ 2'],
    ['employment_type', 'מעמד תעסוקתי · salaried, self_employed, retired, unemployed · ¶ 3'],
    [
      'employment_months',
      'ותק במקום העבודה הנוכחי, או חודשי פעילות כעצמאי · integer, months, optional · ¶ 3',
    ],
    ['monthly_income', 'הכנסה חודשית נטו; לעצמאי, הממוצע ב-12 החודשים האחרונים · number, ₪ · ¶ 4'],
    ['credit_events_24m', 'מספר אירועי אשראי שליליים ב-24 החודשים האחרונים · integer · ¶ 7'],
    ['has_guarantor', 'האם הועמד ערב · boolean, false when absent · ¶ 7'],
    [
      'debt_to_income',
      'יחס החוב להכנסה: החזרים קיימים בתוספת ההחזר החדש, חלקי ההכנסה · derived by R-020',
    ],
  ])('writes the header of %s as the spec does', (name, title) => {
    expect(fieldTitle(field(name), lendingRuleSet)).toBe(title)
  })
})

describe('headerUnit', () => {
  it("names a column's unit beside it, unless the field's name already says it", () => {
    // the spec, section 07: "age years", "requested_amount ₪", "term_months" alone, "credit_events_24m" alone
    expect(headerUnit(field('age'))).toBe('years')
    expect(headerUnit(field('requested_amount'))).toBe('₪')
    expect(headerUnit(field('term_months'))).toBeUndefined()
    expect(headerUnit(field('credit_events_24m'))).toBeUndefined()
  })
})

describe('pointedCell', () => {
  // Document 2: a 422 names the JSON pointers it refused; the table shows each on the cell it names
  it('finds the rule and the field a pointer into a condition names', () => {
    // the document's order: R-010, R-020, R-100, R-110, R-115, R-116, R-120, ...
    expect(pointedCell(lendingRuleSet, '/rules/2/condition/value')).toStrictEqual({
      ruleId: 'R-100',
      field: 'age',
    })
    expect(pointedCell(lendingRuleSet, '/rules/3/condition/all/1/op')).toStrictEqual({
      ruleId: 'R-110',
      field: 'employment_type',
    })
    expect(pointedCell(lendingRuleSet, '/rules/6/condition/not/value/0')).toStrictEqual({
      ruleId: 'R-120',
      field: 'requested_amount',
    })
  })

  it('finds the rule alone when the pointer names no comparison, and nothing outside the rules', () => {
    expect(pointedCell(lendingRuleSet, '/rules/1/actions/0/value')).toStrictEqual({
      ruleId: 'R-020',
    })
    expect(pointedCell(lendingRuleSet, '/fields/2/minimum')).toBeNull()
    expect(pointedCell(lendingRuleSet, '/rules/99/condition')).toBeNull()
  })
})

describe('fieldsOutOfView', () => {
  it('counts the columns that end beyond the visible edge', () => {
    expect(fieldsOutOfView([120, 240, 360, 480, 600], 400)).toBe(2)
    expect(fieldsOutOfView([120, 240], 400)).toBe(0)
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

/**
 * "Since" in the Rules margin (the spec, section 10: "Since v1 · unchanged"): the version a rule stands in as it is now,
 * read from the structural diff of the version before it (Document 3, Structural diff).
 */
describe('sinceOf', () => {
  const r110 = lendingRuleSet.rules.find((rule) => rule.id === 'R-110')!
  const r170 = lendingRuleSet.rules.find((rule) => rule.id === 'R-170')!
  const diff: Diff = {
    fields: { added: [], removed: [], modified: [] },
    rules: {
      added: [{ ...r110, id: 'R-175' }],
      removed: [],
      modified: [{ id: 'R-170', from: r170, to: r170, changes: [] }],
    },
    defaults: null,
  }

  it('names the version before when the rule is unchanged, and the version itself when it changed or is new', () => {
    expect(sinceOf('R-110', 2, { versionNo: 1, diff })).toBe('v1 · unchanged')
    expect(sinceOf('R-170', 2, { versionNo: 1, diff })).toBe('v2 · changed from v1')
    expect(sinceOf('R-175', 2, { versionNo: 1, diff })).toBe('v2 · new')
  })

  it('names the first version as the one the rule was written in', () => {
    expect(sinceOf('R-110', 1, null)).toBe('v1 · first version')
  })
})

describe('withEnabled', () => {
  it('switches one rule off or on and leaves every other rule as it was', () => {
    const off = withEnabled(lendingRuleSet, 'R-310', false)

    // Document 3, Rules: a rule whose enabled is false is skipped by the engine
    expect(off.rules.find((rule) => rule.id === 'R-310')?.enabled).toBe(false)
    expect(off.rules.filter((rule) => rule.id !== 'R-310')).toStrictEqual(
      lendingRuleSet.rules.filter((rule) => rule.id !== 'R-310'),
    )
    expect(withEnabled(off, 'R-310', true).rules.find((rule) => rule.id === 'R-310')?.enabled).toBe(
      true,
    )
  })
})

describe('withRequired', () => {
  // Document 3, Field Schema: a case missing a required field is rejected before evaluation. Expected: employment_months,
  // optional in ruleset.v1.json, made required and back, every other field and every rule as they were
  it('makes one case field required or optional again and leaves the rest of the document as it was', () => {
    const required = withRequired(lendingRuleSet, 'employment_months', true)

    expect(required.fields.find((field) => field.name === 'employment_months')?.required).toBe(true)
    expect(required.fields.filter((field) => field.name !== 'employment_months')).toStrictEqual(
      lendingRuleSet.fields.filter((field) => field.name !== 'employment_months'),
    )
    expect(required.rules).toStrictEqual(lendingRuleSet.rules)
    expect(
      withRequired(required, 'employment_months', false).fields.find(
        (field) => field.name === 'employment_months',
      )?.required,
    ).toBe(false)
  })
})
