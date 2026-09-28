import { describe, expect, it } from 'vitest'
import type { Diff, FieldSchema, Rule } from '../../api/types'
import { scriptedProposalEvent } from '../../test/fixtures/change'
import { lendingRuleSet } from '../../test/fixtures/lending'
import {
  changedAttributes,
  comparisonText,
  diffRows,
  diffSummary,
  labelChange,
  unifiedRows,
} from './diffRows'

// @requirement FR-18
// @requirement FR-20

/**
 * The diff as rows (Document 3, Structural diff: fields by name, rules by id, the defaults as a whole; added and
 * removed items whole, a modified one with both its sides and its changes by JSON pointer). The scripted diff is the
 * one Document 3 abridges, built from the committed fixtures; the other items are rules and fields of the lending
 * rule set itself.
 */

const rule = (id: string): Rule => {
  const found = lendingRuleSet.rules.find((candidate) => candidate.id === id)
  if (found === undefined) {
    throw new Error(`no ${id} in the lending rule set`)
  }
  return found
}

const field = (name: string): FieldSchema => {
  const found = lendingRuleSet.fields.find((candidate) => candidate.name === name)
  if (found === undefined) {
    throw new Error(`no ${name} in the lending rule set`)
  }
  return found
}

/** A rule no version of the lending rule set has: a new gate in the eligibility band. */
const added: Rule = {
  id: 'R-131',
  label: 'דחייה: הלוואה מעל 80,000 ללא ערב',
  priority: 131,
  condition: {
    all: [
      { field: 'requested_amount', op: 'gt', value: 80000 },
      { field: 'has_guarantor', op: 'eq', value: false },
    ],
  },
  actions: [{ type: 'decide', outcome: 'reject', terminal: true, reason: 'נדרש ערב' }],
  provenance: { kind: 'pending', changeRequestId: 'cr-0003', rationale: 'לפי בקשת השינוי' },
}

const empty: Diff = {
  fields: { added: [], removed: [], modified: [] },
  rules: { added: [], removed: [], modified: [] },
  defaults: null,
}

describe('diffRows', () => {
  it('a modified rule is one row with both of its sides and its changes by pointer', () => {
    const [first] = diffRows(scriptedProposalEvent.diff)

    expect(first?.section).toBe('rule')
    expect(first?.key).toBe('R-170')
    expect(first?.kind).toBe('modified')
    expect(first?.before).toStrictEqual(rule('R-170'))
    expect((first?.after as Rule).condition).toStrictEqual({
      field: 'monthly_income',
      op: 'lt',
      value: 9000,
    })
    // Document 3: the view highlights condition.value 8000 → 9000, not the whole rule
    expect(first?.changes).toContainEqual({ path: '/condition/value', from: 8000, to: 9000 })
  })

  it('an added rule has nothing before it, and a removed one nothing after it', () => {
    const rows = diffRows({
      ...empty,
      rules: { added: [added], removed: [rule('R-160')], modified: [] },
    })

    expect(rows).toStrictEqual([
      { section: 'rule', key: 'R-131', kind: 'added', before: null, after: added, changes: [] },
      {
        section: 'rule',
        key: 'R-160',
        kind: 'removed',
        before: rule('R-160'),
        after: null,
        changes: [],
      },
    ])
  })

  it('a modified field is a row of its own, named by the field', () => {
    const before = field('monthly_income')
    const after = { ...before, maximum: 200000 }

    expect(
      diffRows({
        ...empty,
        fields: {
          added: [],
          removed: [],
          modified: [
            {
              name: 'monthly_income',
              from: before,
              to: after,
              changes: [{ path: '/maximum', from: before.maximum, to: 200000 }],
            },
          ],
        },
      }),
    ).toStrictEqual([
      {
        section: 'field',
        key: 'monthly_income',
        kind: 'modified',
        before,
        after,
        changes: [{ path: '/maximum', from: before.maximum, to: 200000 }],
      },
    ])
  })

  it('changed defaults are one row, and unchanged defaults are none', () => {
    const approve = { outcome: 'approve', reason: 'כל בקשה מאושרת' }

    expect(
      diffRows({ ...empty, defaults: { from: lendingRuleSet.defaults, to: approve } }),
    ).toStrictEqual([
      {
        section: 'defaults',
        key: 'defaults',
        kind: 'modified',
        before: lendingRuleSet.defaults,
        after: approve,
        changes: [],
      },
    ])
    expect(diffRows(empty)).toStrictEqual([])
  })

  it('rows come by section, fields first, and by rule number within a section', () => {
    const ten = { ...added, id: 'R-1000', priority: 950 }
    const rows = diffRows({
      fields: {
        added: [],
        removed: [],
        modified: [
          {
            name: 'monthly_income',
            from: field('monthly_income'),
            to: field('monthly_income'),
            changes: [],
          },
        ],
      },
      rules: {
        added: [ten, added],
        removed: [rule('R-160')],
        modified: scriptedProposalEvent.diff.rules.modified,
      },
      defaults: { from: lendingRuleSet.defaults, to: lendingRuleSet.defaults },
    })

    expect(rows.map((row) => row.key)).toStrictEqual([
      'monthly_income',
      'R-131',
      'R-160',
      'R-170',
      'R-410',
      'R-1000',
      'defaults',
    ])
  })
})

describe('diffSummary', () => {
  it('counts rules, then fields, by kind, then says whether the defaults changed', () => {
    const before = field('monthly_income')
    const rows = diffRows({
      fields: {
        added: [{ ...before, name: 'guarantor_income' }],
        removed: [],
        modified: [{ name: 'monthly_income', from: before, to: before, changes: [] }],
      },
      rules: {
        added: [added],
        removed: [],
        modified: scriptedProposalEvent.diff.rules.modified,
      },
      defaults: { from: lendingRuleSet.defaults, to: lendingRuleSet.defaults },
    })

    expect(diffSummary(rows)).toBe(
      '1 rule added · 2 rules modified · 1 field added · 1 field modified · the defaults changed',
    )
    expect(diffSummary(diffRows(scriptedProposalEvent.diff))).toBe('2 rules modified')
    expect(diffSummary([])).toBe('')
  })
})

describe('changedAttributes', () => {
  it('marks the attribute a pointer falls in, once however many leaves of it changed', () => {
    const r410 = scriptedProposalEvent.diff.rules.modified[1]

    expect(r410?.id).toBe('R-410')
    expect(changedAttributes(r410?.changes ?? [])).toStrictEqual(
      new Set(['condition', 'provenance']),
    )
  })

  it('marks the label, the condition, the actions and the provenance of the scripted R-170', () => {
    const r170 = scriptedProposalEvent.diff.rules.modified[0]

    expect(changedAttributes(r170?.changes ?? [])).toStrictEqual(
      new Set(['label', 'condition', 'actions', 'provenance']),
    )
  })
})

/**
 * The unified diff (the spec, section 09, "The change request": "one row per changed cell (rule · field · before →
 * after) with the changed value tinted, the whole rule struck when removed"): a modified rule is a row for each
 * comparison of its condition that changed, named by the field it tests, then one for each other attribute that
 * changed; an added or a removed rule is one row, whole.
 */
describe('unifiedRows', () => {
  it("writes the scripted change as the spec's rows: each changed comparison by its field, then the label, the action and the source", () => {
    const rows = unifiedRows(scriptedProposalEvent.diff)

    expect(rows.map((row) => [row.key, row.field, row.kind])).toStrictEqual([
      ['R-170', 'monthly_income', 'condition'],
      ['R-170', 'label', 'label'],
      ['R-170', 'action', 'action'],
      ['R-170', 'source', 'source'],
      ['R-410', 'monthly_income', 'condition'],
      ['R-410', 'source', 'source'],
    ])
    // Document 3: condition.value 8000 → 9000, and R-410's band by its two ends
    expect(rows[0]).toMatchObject({
      before: { field: 'monthly_income', op: 'lt', value: 8000 },
      after: { field: 'monthly_income', op: 'lt', value: 9000 },
    })
    expect(rows[4]).toMatchObject({
      before: { op: 'between', value: [8000, 9000] },
      after: { op: 'between', value: [9000, 10000] },
    })
  })

  it('names a comparison inside an all by the field it tests, and writes an added or a removed rule whole', () => {
    const gate: Rule = { ...added, id: 'R-132' }
    const tightened: Rule = {
      ...gate,
      condition: {
        all: [
          { field: 'requested_amount', op: 'gt', value: 60000 },
          { field: 'has_guarantor', op: 'eq', value: false },
        ],
      },
    }
    const rows = unifiedRows({
      ...empty,
      rules: {
        added: [added],
        removed: [rule('R-160')],
        modified: [
          {
            id: 'R-132',
            from: gate,
            to: tightened,
            changes: [{ path: '/condition/all/0/value', from: 80000, to: 60000 }],
          },
        ],
      },
    })

    expect(rows.map((row) => [row.key, row.field, row.kind])).toStrictEqual([
      ['R-131', '', 'added'],
      ['R-132', 'requested_amount', 'condition'],
      ['R-160', '', 'removed'],
    ])
    expect(rows[1]).toMatchObject({
      before: { field: 'requested_amount', op: 'gt', value: 80000 },
      after: { field: 'requested_amount', op: 'gt', value: 60000 },
    })
  })

  it('writes a field and the defaults as rows of their own', () => {
    const rows = unifiedRows({
      ...empty,
      fields: { added: [field('has_guarantor')], removed: [], modified: [] },
      defaults: {
        from: { outcome: 'refer', reason: 'לבדיקה' },
        to: { outcome: 'reject', reason: 'נדחה' },
      },
    })

    expect(rows.map((row) => [row.key, row.field, row.kind])).toStrictEqual([
      ['has_guarantor', 'field', 'added'],
      ['defaults', 'defaults', 'defaults'],
    ])
  })
})

/**
 * A label that changed (the spec's unified diff: "…נמוכה מ-8,000 → …נמוכה מ-9,000"): the words around the change kept
 * for context, one before it, the rest cut, and the changed part whole: a number is tinted whole, never a digit of it.
 */
describe('labelChange', () => {
  it("keeps one word before the change, cuts the rest, and tints the number whole: the spec's R-170", () => {
    expect(
      labelChange('דחייה: הכנסה חודשית נטו נמוכה מ-8,000', 'דחייה: הכנסה חודשית נטו נמוכה מ-9,000'),
    ).toStrictEqual({
      before: { cut: true, head: 'נמוכה מ-', changed: '8,000', tail: '', cutEnd: false },
      after: { cut: true, head: 'נמוכה מ-', changed: '9,000', tail: '', cutEnd: false },
    })
  })

  it('cuts nothing from a label that changed at its start, and keeps one word after the change', () => {
    expect(labelChange('דחייה בגלל גיל המבקש', 'אישור בגלל גיל המבקש')).toStrictEqual({
      before: { cut: false, head: '', changed: 'דחייה', tail: ' בגלל', cutEnd: true },
      after: { cut: false, head: '', changed: 'אישור', tail: ' בגלל', cutEnd: true },
    })
  })
})

describe('comparisonText', () => {
  it('writes a comparison as the unified diff does, without its tints: the sign, then the value', () => {
    // the spec's audit log, section 09: "R-170 monthly_income < 8,000 → < 9,000 · R-410 [8,000 .. 9,000] → …"
    expect(comparisonText({ field: 'monthly_income', op: 'lt', value: 8000 })).toBe('< 8,000')
    expect(comparisonText({ field: 'monthly_income', op: 'between', value: [8000, 9000] })).toBe(
      '[8,000 .. 9,000]',
    )
    // the rules table's signs (the spec, section 07)
    expect(comparisonText({ field: 'has_guarantor', op: 'eq', value: false })).toBe('= false')
  })
})
