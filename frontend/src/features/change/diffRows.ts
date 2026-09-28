import type { Diff, DiffChange } from '../../api/types'
import { conditionText, literalText } from '../rules/cellGrammar'

/**
 * The structural diff as the rows of the side-by-side view (Document 3, Structural diff: fields by name, rules by id,
 * the defaults as a whole). The rows are read from the diff's JSON alone and never recomputed from the documents,
 * because an approval's audit entry stores that JSON and the view shows it as it was stored.
 */

export type DiffSection = 'field' | 'rule' | 'defaults'
export type DiffKind = 'added' | 'removed' | 'modified'

export interface DiffRow {
  section: DiffSection
  /** The field's name, the rule's id, or `defaults`. */
  key: string
  kind: DiffKind
  /** The item in the earlier version, whole; null for an added one. */
  before: unknown
  /** The item in the later version, whole; null for a removed one. */
  after: unknown
  /** What changed, by JSON pointer into the item; the defaults are compared whole, so theirs is empty. */
  changes: DiffChange[]
}

const KINDS: DiffKind[] = ['added', 'removed', 'modified']

/** A rule id's number, so R-1000 comes after R-410 (Document 3: `R-` and two to four digits). */
function ruleNumber(id: string): number {
  const match = /^R-(\d+)$/.exec(id)
  return match === null ? Number.MAX_SAFE_INTEGER : Number(match[1])
}

function byKey(section: DiffSection): (left: DiffRow, right: DiffRow) => number {
  return (left, right) => {
    if (section === 'rule') {
      const byNumber = ruleNumber(left.key) - ruleNumber(right.key)
      if (byNumber !== 0) {
        return byNumber
      }
    }
    return left.key < right.key ? -1 : left.key > right.key ? 1 : 0
  }
}

function keyOf(item: unknown, key: 'name' | 'id'): string {
  const value = (item as Record<string, unknown> | null)?.[key]
  return typeof value === 'string' ? value : ''
}

/** The rows of a diff: fields first, then rules, then the defaults; within a section by name, rules by number. */
export function diffRows(diff: Diff): DiffRow[] {
  const whole =
    (section: DiffSection, key: 'name' | 'id', kind: 'added' | 'removed') =>
    (item: unknown): DiffRow => ({
      section,
      key: keyOf(item, key),
      kind,
      before: kind === 'removed' ? item : null,
      after: kind === 'added' ? item : null,
      changes: [],
    })
  const fields: DiffRow[] = [
    ...diff.fields.added.map(whole('field', 'name', 'added')),
    ...diff.fields.removed.map(whole('field', 'name', 'removed')),
    ...diff.fields.modified.map((item) => ({
      section: 'field' as const,
      key: item.name,
      kind: 'modified' as const,
      before: item.from,
      after: item.to,
      changes: item.changes,
    })),
  ].sort(byKey('field'))
  const rules: DiffRow[] = [
    ...diff.rules.added.map(whole('rule', 'id', 'added')),
    ...diff.rules.removed.map(whole('rule', 'id', 'removed')),
    ...diff.rules.modified.map((item) => ({
      section: 'rule' as const,
      key: item.id,
      kind: 'modified' as const,
      before: item.from,
      after: item.to,
      changes: item.changes,
    })),
  ].sort(byKey('rule'))
  const sides = diff.defaults as { from: unknown; to: unknown } | null
  const defaults: DiffRow[] =
    sides === null
      ? []
      : [
          {
            section: 'defaults',
            key: 'defaults',
            kind: 'modified',
            before: sides.from,
            after: sides.to,
            changes: [],
          },
        ]
  return [...fields, ...rules, ...defaults]
}

/** One line over the view: the rules, then the fields, counted by kind, then whether the defaults changed. */
export function diffSummary(rows: DiffRow[]): string {
  const parts: string[] = []
  for (const section of ['rule', 'field'] as const) {
    for (const kind of KINDS) {
      const count = rows.filter((row) => row.section === section && row.kind === kind).length
      if (count > 0) {
        parts.push(`${count} ${section}${count === 1 ? '' : 's'} ${kind}`)
      }
    }
  }
  if (rows.some((row) => row.section === 'defaults')) {
    parts.push('the defaults changed')
  }
  return parts.join(' · ')
}

/** The top-level attributes the changes fall in: `/condition/value/0` is the condition. */
export function changedAttributes(changes: DiffChange[]): Set<string> {
  return new Set(
    changes.map((change) =>
      (change.path.split('/')[1] ?? '').replaceAll('~1', '/').replaceAll('~0', '~'),
    ),
  )
}

/**
 * One row of the unified diff (the spec, section 09: "one row per changed cell, rule · field · before → after"): a
 * comparison of a modified rule's condition by the field it tests, another attribute of it by name, a rule or a field
 * added or removed whole, a field modified, or the defaults.
 */
export interface UnifiedRow {
  section: DiffSection
  /** The rule's id, the field's name, or `defaults`. */
  key: string
  /** The field a comparison tests, the attribute that changed, `field` for a field, or empty for a rule whole. */
  field: string
  kind:
    | 'condition'
    | 'label'
    | 'priority'
    | 'enabled'
    | 'action'
    | 'source'
    | 'tags'
    | 'added'
    | 'removed'
    | 'field'
    | 'defaults'
  before: unknown
  after: unknown
}

/** The attributes of a modified rule other than its condition, in the order the unified diff lists them. */
const ATTRIBUTES: [path: string, kind: UnifiedRow['kind'], field: string][] = [
  ['label', 'label', 'label'],
  ['priority', 'priority', 'priority'],
  ['enabled', 'enabled', 'enabled'],
  ['actions', 'action', 'action'],
  ['provenance', 'source', 'source'],
  ['tags', 'tags', 'tags'],
]

/** The node a JSON pointer names inside a value, or undefined. */
function at(value: unknown, pointer: string[]): unknown {
  return pointer.reduce<unknown>(
    (node, token) =>
      node === null || typeof node !== 'object'
        ? undefined
        : (node as Record<string, unknown>)[token],
    value,
  )
}

/** Whether a node of a condition is a comparison: it names the field it tests. */
function isComparison(node: unknown): node is { field: string } {
  return (
    node !== null &&
    typeof node === 'object' &&
    typeof (node as { field?: unknown }).field === 'string'
  )
}

/**
 * The comparisons of a modified rule's condition that its changes fall in, each by its pointer, in the order they
 * first change; a change that falls in no single comparison stands for the whole condition.
 */
function changedComparisons(from: unknown, to: unknown, changes: DiffChange[]): string[][] {
  const pointers: string[][] = []
  for (const change of changes) {
    const tokens = change.path
      .split('/')
      .slice(1)
      .map((token) => token.replaceAll('~1', '/').replaceAll('~0', '~'))
    if (tokens[0] !== 'condition') {
      continue
    }
    let pointer = tokens
    while (
      pointer.length > 1 &&
      !(isComparison(at(from, pointer)) || isComparison(at(to, pointer)))
    ) {
      pointer = pointer.slice(0, -1)
    }
    if (!pointers.some((known) => known.join('/') === pointer.join('/'))) {
      pointers.push(pointer)
    }
  }
  return pointers
}

/**
 * The unified diff's rows: fields first, then rules by number, a modified rule's changed cells in order, then the
 * defaults.
 */
export function unifiedRows(diff: Diff): UnifiedRow[] {
  const rows: UnifiedRow[] = []
  for (const row of diffRows(diff)) {
    if (row.section === 'defaults') {
      rows.push({
        section: 'defaults',
        key: 'defaults',
        field: 'defaults',
        kind: 'defaults',
        before: row.before,
        after: row.after,
      })
    } else if (row.kind !== 'modified') {
      rows.push({
        section: row.section,
        key: row.key,
        field: row.section === 'field' ? 'field' : '',
        kind: row.kind,
        before: row.before,
        after: row.after,
      })
    } else if (row.section === 'field') {
      rows.push({
        section: 'field',
        key: row.key,
        field: 'field',
        kind: 'field',
        before: row.before,
        after: row.after,
      })
    } else {
      for (const pointer of changedComparisons(row.before, row.after, row.changes)) {
        const before = at(row.before, pointer)
        const after = at(row.after, pointer)
        const tested = isComparison(after)
          ? after.field
          : isComparison(before)
            ? before.field
            : 'condition'
        rows.push({
          section: 'rule',
          key: row.key,
          field: tested,
          kind: 'condition',
          before,
          after,
        })
      }
      const changed = changedAttributes(row.changes)
      for (const [path, kind, field] of ATTRIBUTES) {
        if (changed.has(path)) {
          rows.push({
            section: 'rule',
            key: row.key,
            field,
            kind,
            before: (row.before as Record<string, unknown>)[path],
            after: (row.after as Record<string, unknown>)[path],
          })
        }
      }
    }
  }
  return rows
}

/** One side of a label that changed: the words kept before the change, the change, and a word after it. */
export interface LabelSide {
  /** More of the label precedes what is kept, which the view writes as an ellipsis. */
  cut: boolean
  head: string
  changed: string
  tail: string
  /** More of the label follows the tail. */
  cutEnd: boolean
}

/** A character a number or a word is made of: a changed number is tinted whole, never one digit of it. */
const PART_OF_A_TOKEN = /[\p{L}\p{N},.]/u

/**
 * A label that changed, as the unified diff writes it (the spec, section 09: "…נמוכה מ-8,000 → …נמוכה מ-9,000"): the
 * part that differs, widened to whole numbers and words, with one word of context before it and one after it, and the
 * rest cut.
 */
export function labelChange(
  before: string,
  after: string,
): { before: LabelSide; after: LabelSide } {
  let start = 0
  while (start < before.length && start < after.length && before[start] === after[start]) {
    start++
  }
  let end = 0
  while (
    end < before.length - start &&
    end < after.length - start &&
    before[before.length - 1 - end] === after[after.length - 1 - end]
  ) {
    end++
  }
  // widen the change to whole numbers and words: "8,000" against "9,000" is tinted whole, not its first digit
  while (start > 0 && PART_OF_A_TOKEN.test(before[start - 1]!)) {
    start--
  }
  while (end > 0 && PART_OF_A_TOKEN.test(before[before.length - end]!)) {
    end--
  }
  const side = (text: string): LabelSide => {
    const words = text.slice(0, start).split(' ')
    const kept = words.slice(-2)
    const rest = text.slice(text.length - end)
    const following = rest.split(' ')
    return {
      cut: words.length > kept.length,
      head: kept.join(' '),
      changed: text.slice(start, text.length - end),
      tail: following.slice(0, 2).join(' '),
      cutEnd: following.length > 2,
    }
  }
  return { before: side(before), after: side(after) }
}

/** The signs the rules table writes a comparison with (the spec, section 07). */
export const SIGNS: Record<string, string> = {
  lt: '<',
  lte: '≤',
  gt: '>',
  gte: '≥',
  eq: '=',
  ne: '≠',
}

/** A compared value as the diff writes it: a literal in the rules table's grammar, a list as JSON, none as "none". */
export function valueText(value: unknown): string {
  return value === null || value === undefined
    ? 'none'
    : typeof value === 'object'
      ? JSON.stringify(value)
      : literalText(value)
}

/**
 * A comparison as the unified diff writes it, without its tints: the sign, then the value ("< 8,000"), a range between
 * its ends ("[8,000 .. 9,000]"); anything else in the rules table's grammar. The audit log writes an approval's
 * changes with it.
 */
export function comparisonText(leaf: unknown): string {
  const node = leaf as { op?: string; value?: unknown } | undefined
  if (node?.op === undefined) {
    return node === undefined ? '' : conditionText(node, new Map())
  }
  if (node.op === 'between' && Array.isArray(node.value)) {
    const [low, high] = node.value as [unknown, unknown]
    return `[${literalText(low)} .. ${literalText(high)}]`
  }
  return `${SIGNS[node.op] ?? node.op} ${valueText(node.value)}`
}
